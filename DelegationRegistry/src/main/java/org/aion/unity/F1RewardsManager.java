package org.aion.unity;

import avm.Address;
import org.aion.unity.model.DelegatorStartingInfo;
import org.aion.unity.model.HistoricalReward;
import org.aion.unity.model.HistoricalRewardsStore;

import java.util.*;

/**
 * @author github.com/{ali-sharif, iamyulong}
 *
 * Implementation Notice: This reference implementation requires audit and potential rework. Do not use in production.
 *
 * This is an implementation of Dev Ojha's F1 rewards distribution scheme:
 * https://github.com/cosmos/cosmos-sdk/pull/3099
 * https://github.com/cosmos/cosmos-sdk/blob/master/docs/spec/_proposals/f1-fee-distribution/f1_fee_distr.pdf
 *
 * TODO: Use correct decimal-handling. Current implementation uses double precision floating points for rewards.
 * ref: https://github.com/cosmos/cosmos-sdk/blob/master/types/dec_coin.go
 *
 * Key classes in the cosmos delegation model:
 * 1. Top level delegation interactions:
 * https://github.com/cosmos/cosmos-sdk/blob/master/x/staking/keeper/delegation.go
 *
 * 2. Mid-level "hooks" called by the top-level implementation
 * https://github.com/cosmos/cosmos-sdk/blob/master/x/staking/keeper/hooks.go
 *
 * 3. Low-level implementation
 * https://github.com/cosmos/cosmos-sdk/blob/master/x/distribution/keeper/delegation.go
 */

@SuppressWarnings("unused")
public class F1RewardsManager extends RewardsManager {

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    private class PoolStateMachine {
        // pool variables
        private final double fee;

        // state variables
        long currentStake; // stake accumulated in the pool
        double accumulatedCommission; // commission accumulated by the commission

        double outstandingRewards; // total rewards owned by the pool
        double withdrawnRewards; // total rewards withdrawn from the pool

        long currentPeriod; // current period number
        double currentRewards; // rewards accumulated this period

        HistoricalRewardsStore history;
        Map<Address, DelegatorStartingInfo> delegations; // total delegations per delegator

        // Initialize pool
        public PoolStateMachine(double fee) {
            this.fee = fee;

            currentPeriod = 1; // accounting starts at period = 1

            history = new HistoricalRewardsStore();
            history.setHistoricalReward(0, new HistoricalReward(0D)); // set period 0 reward to be 0

            delegations = new HashMap<>();
        }

        private long prevPeriod() {
            if (currentPeriod == 0)
                throw new RuntimeException("Last period cannot be negative");

            return currentPeriod - 1;
        }

        private long nextPeriod() {
            return currentPeriod + 1;
        }

        // increment validator period, returning the period just ended
        long incrementValidatorPeriod() {
            double currentCRR = 0;
            if (currentStake > 0) {
                currentCRR = currentRewards / currentStake;
            }

            // fetch CRR for last period to obtain the CRR
            double prevCRR = history.getHistoricalReward(prevPeriod()).cumulativeRewardRatio;
            double nextCRR = prevCRR + currentCRR;

            // decrement reference count
            history.decrementReferenceCount(prevPeriod());

            // set new historical rewards with reference count of 1
            history.setHistoricalReward(currentPeriod, new HistoricalReward(nextCRR));

            // set current rewards, incrementing period by 1
            currentRewards = 0;
            currentPeriod++;

            return prevPeriod();
        }

        // I guess
        public void unBond(Address delegator, long stake, long blockNumber) {
            if (!delegations.containsKey(delegator))
                throw new RuntimeException("Delegator has no staked");

            double prevBond = delegations.get(delegator).stake;
            if (stake > prevBond)
                throw new RuntimeException("Cannot un-bond more than bonded stake");

            withdrawDelegationRewards(delegator, blockNumber);

            double nextBond = prevBond - stake;

            if (nextBond == 0) {
                delegations.remove(delegator);
            } else {
                initializeDelegation(delegator, nextBond, blockNumber);
            }
        }

        public void onDelegate(Address delegator, long stake, long blockNumber) {
            double prevDelegation = 0D;
            // first check if this is a new delegation or an updated delegation
            if (!delegations.containsKey(delegator)) { // new delegation
                incrementValidatorPeriod();
            } else { // update delegation
                // withdraw delegation rewards (which also increments period and cleans out the old delegation)
                withdrawDelegationRewards(delegator, blockNumber);
                prevDelegation = delegations.get(delegator).stake;
            }

            double nextDelegation = prevDelegation + stake;

            initializeDelegation(delegator, stake, blockNumber);
        }

        public double onWithdrawDelegator(Address delegator, long blockNumber) {
            if (!delegations.containsKey(delegator))
                throw new RuntimeException("Delegator has no staked");

            double prevDelegation = delegations.get(delegator).stake;
            double w = withdrawDelegationRewards(delegator, blockNumber);

            initializeDelegation(delegator, prevDelegation, blockNumber);
            withdrawnRewards -= w;

            return w;
        }

        public double onWithdrawPoolOperator(Address delegator) {
            double temp = accumulatedCommission;
            accumulatedCommission = 0;

            outstandingRewards -= temp;
            withdrawnRewards -= temp;

            return temp;
        }

        /**
         * This method is called twice:
         * 1. once when a delegation is newly created or modified
         * 2. when someone withdraws their delegation rewards
         */
        private void initializeDelegation(Address delegator, double stake, long blockNumber) {
            // increment reference count for the period we're going to track
            history.incrementReferenceCount(prevPeriod());

            if (delegations.containsKey(delegator))
                throw new RuntimeException("Attempted to initialize an already-initialized delegator");

            delegations.put(delegator, new DelegatorStartingInfo(prevPeriod(), stake, blockNumber));
        }

        public double calculateDelegationRewards(Address delegator, long endingPeriod, long currentBlockNumber) {
            if (!delegations.containsKey(delegator))
                throw new RuntimeException("Delegator not initialized correctly");

            DelegatorStartingInfo stakingInfo = delegations.get(delegator);

            if (stakingInfo.blockNumber < currentBlockNumber)
                throw new RuntimeException("Cannot calculate delegation rewards for blocks before stake was delegated");

            if (stakingInfo.blockNumber == currentBlockNumber)
                return 0D;

            long startingPeriod = stakingInfo.previousPeriod;
            double stake = stakingInfo.stake;

            // return stake * (ending - starting)
            double startingCRR = history.getHistoricalReward(startingPeriod).cumulativeRewardRatio;
            double endingCRR = history.getHistoricalReward(endingPeriod).cumulativeRewardRatio;
            double differenceCRR = endingCRR - startingCRR;

            if (differenceCRR < 0) {
                throw new RuntimeException("Negative rewards should not be possible");
            }

            return (differenceCRR * stake);
        }

        /**
         * Implementation Note: cannot withdraw partial amounts, for storage efficiency
         *
         */
        private double withdrawDelegationRewards(Address delegator, long currentBlockNumber) {
            assert (delegator != null); // sanity check

            if (!delegations.containsKey(delegator))
                throw new RuntimeException("Attempted to withdraw rewards for non-existent delegator");

            long endingPeriod = incrementValidatorPeriod();
            double rewards = calculateDelegationRewards(delegator, endingPeriod, currentBlockNumber);

            outstandingRewards -= rewards;

            DelegatorStartingInfo startingInfo = delegations.get(delegator);
            history.decrementReferenceCount(startingInfo.previousPeriod);

            delegations.remove(delegator);
            return rewards;
        }
        
        /**
         * On block production, we need to withhold the pool commission, and update the rewards managed by this pool.
         * ref: https://github.com/cosmos/cosmos-sdk/blob/master/x/distribution/keeper/allocation.go
         */
        void onBlockProduced(long blockNumber, long blockReward) {
            assert (blockNumber > 0 && blockReward > 0); // sanity check

            double commission = fee * blockReward;
            double shared = (double)blockReward - commission;

            this.accumulatedCommission += commission;
            this.currentRewards += shared;
            this.outstandingRewards += (double)blockReward;
        }
    }

    @Override
    public Map<Address, Long> computeRewards(List<Event> events) {
        return null;
    }
}

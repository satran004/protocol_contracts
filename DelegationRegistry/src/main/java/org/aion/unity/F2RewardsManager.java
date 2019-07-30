package org.aion.unity;

import avm.Address;
import org.aion.unity.model.DelegatorStartingInfo;
import org.aion.unity.model.HistoricalReward;
import org.aion.unity.model.HistoricalRewardsStore;

import java.util.*;

@SuppressWarnings("unused")
public class F2RewardsManager extends RewardsManager {

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess", "MismatchedQueryAndUpdateOfCollection"})
    private class PoolStateMachine {
        // pool variables
        private final double fee;

        // state variables
        private long accumulatedStake; // stake accumulated in the pool

        // commission is handled separately
        private double accumulatedCommission;
        private double withdrawnCommission;

        private double outstandingRewards; // total coins (as rewards) owned by the pool

        private long currentPeriod; // current period number
        private double currentRewards; // rewards accumulated this period

        private Map<Address, Double> settledRewards = new HashMap<>(); // rewards in the "settled" state
        private Map<Address, Double> withdrawnRewards = new HashMap<>(); // rewards withdrawn from the pool, by each delegator

        private HistoricalRewardsStore history;
        private Map<Address, DelegatorStartingInfo> delegations; // total delegations per delegator

        public double getBondedStake(Address delegator) {
            return delegations.containsKey(delegator) ? delegations.get(delegator).stake : 0d;
        }

        public double getSettledRewards(Address delegator) {
            return settledRewards.getOrDefault(delegator, 0d);
        }

        public double getWithdrawnRewards(Address delegator) {
            return withdrawnRewards.getOrDefault(delegator, 0d);
        }

        public double getOwedRewards(Address delegator, long blockNumber) {
            double unsettledRewards = calculateUnsettledRewards(delegator, blockNumber, currentPeriod);
            double settledRewards = getSettledRewards(delegator);
            return unsettledRewards + settledRewards;
        }

        // Initialize pool
        public PoolStateMachine(double fee) {
            this.fee = fee;

            currentPeriod = 1; // accounting starts at period = 1

            history = new HistoricalRewardsStore();
            history.setHistoricalReward(0, new HistoricalReward(0D)); // set period 0 reward to be 0

            delegations = new HashMap<>();
        }

        /* ----------------------------------------------------------------------
         * Leave and Join Functions
         * ----------------------------------------------------------------------*/
        /**
         * @return the bonded stake that just "left"
         */
        private double leave(Address delegator, long blockNumber) {
            assert (delegator != null && delegations.containsKey(delegator)); // sanity check

            long endingPeriod = incrementValidatorPeriod();
            double rewards = calculateUnsettledRewards(delegator, endingPeriod, blockNumber);

            outstandingRewards -= rewards;
            settledRewards.put(delegator, rewards + settledRewards.getOrDefault(delegator, 0d));

            DelegatorStartingInfo startingInfo = delegations.get(delegator);
            double stake = startingInfo.stake;

            history.decrementReferenceCount(startingInfo.previousPeriod);

            delegations.remove(delegator);

            return stake;
        }

        private void join(Address delegator, long blockNumber, double stake) {
            assert (delegator != null && !delegations.containsKey(delegator)); // sanity check

            // increment reference count for the period we're going to track
            history.incrementReferenceCount(prevPeriod());

            // add this new delegation to our store
            delegations.put(delegator, new DelegatorStartingInfo(prevPeriod(), stake, blockNumber));
        }

        /* ----------------------------------------------------------------------
         * "Internal" Functions used by Leave and Join
         * ----------------------------------------------------------------------*/
        private long prevPeriod() { assert (currentPeriod > 0); return currentPeriod - 1; }
        private long nextPeriod() { return currentPeriod + 1; }

        // increment validator period, returning the period just ended
        private long incrementValidatorPeriod() {
            double currentCRR = 0;
            if (accumulatedStake > 0) {
                currentCRR = currentRewards / accumulatedStake;
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

        private double calculateUnsettledRewards(Address delegator, long blockNumber, long endingPeriod) {
            DelegatorStartingInfo stakingInfo = delegations.get(delegator);

            if (stakingInfo.blockNumber < blockNumber)
                throw new RuntimeException("Cannot calculate delegation rewards for blocks before stake was delegated");

            if (stakingInfo.blockNumber == blockNumber)
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

        /* ----------------------------------------------------------------------
         * Contract Lifecycle Functions
         * ----------------------------------------------------------------------*/
        public void onUnvote(Address delegator, long blockNumber, double stake) {
            assert (delegations.containsKey(delegator));
            double prevBond = delegations.get(delegator).stake;
            assert (stake > prevBond); // make sure the amount of unvote requested is legal.

            double unbondedStake = leave(delegator, blockNumber);
            assert (unbondedStake == prevBond);

            // if they didn't fully un-bond, re-bond the remaining amount
            double nextBond = prevBond - stake;
            if (nextBond > 0) {
                join(delegator, blockNumber, nextBond);
            }
        }

        public void onVote(Address delegator, long blockNumber, double stake) {
            assert (stake > 0);

            double prevBond = 0d;
            if (delegations.containsKey(delegator))
                prevBond = leave(delegator, blockNumber);

            double nextBond = prevBond + stake;
            join(delegator, blockNumber, nextBond);
        }

        /**
         * Withdraw is all or nothing, since that is both simpler, implementation-wise and does not make
         * much sense for people to partially withdraw. The problem we run into is that if the amount requested
         * for withdraw, can be less than the amount settled, in which case, it's not obvious if we should perform
         * a settlement ("leave") or save on gas and just withdraw out the rewards.
         */
        public void onWithdraw(Address delegator, long blockNumber) {
            // do a "leave-and-join"
            double unbondedStake = leave(delegator, blockNumber);
            join(delegator, blockNumber, unbondedStake);

            // now that all rewards owed to you are settled, you can withdraw them all at once
            double rewards = settledRewards.getOrDefault(delegator, 0d);
            settledRewards.remove(delegator);

            withdrawnRewards.put(delegator, rewards + withdrawnRewards.getOrDefault(delegator, 0d));
            outstandingRewards -= rewards;
        }

        public double onWithdrawOperator() {
            double c = accumulatedCommission;
            accumulatedCommission = 0;

            withdrawnCommission += c;
            outstandingRewards -= c;

            return c;
        }

        /**
         * On block production, we need to withhold the pool commission, and update the rewards managed by this pool.
         * ref: https://github.com/cosmos/cosmos-sdk/blob/master/x/distribution/keeper/allocation.go
         */
        public void onBlock(long blockNumber, double blockReward) {
            assert (blockNumber > 0 && blockReward > 0); // sanity check

            double commission = fee * blockReward;
            double shared = blockReward - commission;

            this.accumulatedCommission += commission;
            this.currentRewards += shared;
            this.outstandingRewards += blockReward;
        }
    }

    @Override
    public Map<Address, Double> computeRewards(List<Event> events) {
        PoolStateMachine sm = new PoolStateMachine(0.2D);
        Set<Address> addresses = new HashSet<>();

        assert (events.size() > 0);

        // to remember the last block seen, for owed calculation at the end
        long blockNumber = events.get(0).blockNumber;
        for (Event event : events) {
            Address delegator = event.source;
            blockNumber = event.blockNumber;
            double amount = event.amount;

            switch (event.type) {
                case VOTE: {
                    addresses.add(delegator);
                    sm.onVote(delegator, blockNumber, amount);
                    break;
                }
                case UNVOTE: {
                    addresses.add(delegator);
                    sm.onUnvote(delegator, blockNumber, amount);
                    break;
                }
                case WITHDRAW: {
                    addresses.add(delegator);
                    sm.onWithdraw(delegator, blockNumber);
                    break;
                }
                case BLOCK: {
                    sm.onBlock(blockNumber, amount);
                    break;
                }
            }
        }

        // finalize the owed + withdrawn rewards
        Map<Address, Double> rewards = new HashMap<>();
        for (Address a : addresses) {
            double r = sm.getOwedRewards(a, blockNumber) + sm.getWithdrawnRewards(a);
            rewards.put(a, r);
        }

        return rewards;
    }
}

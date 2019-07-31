package org.aion.unity.distribution;

import avm.Address;
import org.aion.unity.distribution.schemes.F1ToyRewardsManager;
import org.aion.unity.distribution.schemes.SimpleRewardsManager;
import org.aion.unity.distribution.model.RewardsManager;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.DoubleStream;

public class FuzzingTest {

    @Test
    public void testVectorSimple() {
        final double REWARD = 5_000_000L;

        // Generate test vector
        List<RewardsManager.Event> events = new ArrayList<>();
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(1), 1, 3400d));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(2), 1, 5700d));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(2), 2, 2000d));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 2, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(3), 3, 6000d));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(4), 4, 1500d));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(5), 4, 6000d));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 7, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 8, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 11, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(2), 12, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(3), 12, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(4), 12, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, addressOf(5), 12, 1200d));
        events.add(new RewardsManager.Event(RewardsManager.EventType.UNVOTE, addressOf(2), 12, 3000d));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 25, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(5), 26, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, 32, REWARD));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(4), 36, null));
        events.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, addressOf(5), 36, null));

        RewardsManager simple = new SimpleRewardsManager();
        Map<Address, Double> r0 = simple.computeRewards(events);

        RewardsManager f1 = new F1ToyRewardsManager();
        Map<Address, Double> r1 = f1.computeRewards(events);

        System.out.println(r0);
        System.out.println(r1);
        double[] error1 = calcErrorSD(r0, r1);
        System.out.printf("Error (F1): mean = %.2f%%, sd = %.2f%%\n", error1[0], error1[1]);
    }

    @Test
    public void testVectorAutogen() {
        // generated vector
        List<RewardsManager.Event> events = new ArrayList<>();

        // system params
        int users = 100;
        long maxUserBalance = 25000;
        long startBlock = 1;
        long endBlock = 1000;
        int maxActionsPerBlock = 3;
        double blockReward = 5_000_000L;
        float poolProbability = 0.8f; // pool's probability of winning a block.

        SimpleRewardsManager rm = new SimpleRewardsManager();

        // abuse the simple rewards manager a little bit, by running it every time we add a new entry,
        // to see what we can do next. probably a much better way to do this, but whatever ...
        for (long i = startBlock; i <= endBlock; i++) {

            // temporary list where we accumuate the events in this block
            List<RewardsManager.Event> v = new ArrayList<>();

            Set<Address> usersInThisRound = new HashSet<>();

            // pick a certain number of actions to do in this round
            int numActions = getRandomInt(0, maxActionsPerBlock);
            for (int j = 0; j < numActions; j++) {

                // choose a random user, not already used in this round
                Address user;
                do {
                    user = addressOf(getRandomInt(1, users));
                } while (usersInThisRound.contains(user));
                usersInThisRound.add(user);

                // choose a random action to do for the user ...

                // if the user hasn't staked already start with that
                if (!rm.getStakeMap().containsKey(user)) {
                    v.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, user, i, (double)getRandomLong(1, maxUserBalance)));
                } else {
                    // choose between withdraw, unvote or vote more, with 1/3 probability
                    int choice = getRandomInt(1, 3);
                    double stakedBalance = rm.getStakeMap().getOrDefault(user, 0d);

                    if (choice == 1) { // vote more
                        double remainingBalance = maxUserBalance - stakedBalance;
                        v.add(new RewardsManager.Event(RewardsManager.EventType.VOTE, user, i, (double)getRandomLong(1, (long)Math.floor(remainingBalance))));
                    } else if (choice == 2) { // unvote
                        if (stakedBalance > 0L)
                            v.add(new RewardsManager.Event(RewardsManager.EventType.UNVOTE, user, i, (double)getRandomLong(1, (long)Math.floor(stakedBalance))));
                    } else if (choice == 3 && stakedBalance > 0L) { // withdraw
                        double pendingRewards = rm.getPendingRewardMap().getOrDefault(user, 0d);

                        if (pendingRewards > 0)
                            v.add(new RewardsManager.Event(RewardsManager.EventType.WITHDRAW, user, i, null));


                    }
                }
            }

            // decide if this block will be produced by this pool
            if (coinWithProbability(poolProbability)) {
                v.add(new RewardsManager.Event(RewardsManager.EventType.BLOCK, null, i, blockReward));
            }

            // apply the rewards this round to the simple rewards manager
            try {
                rm.computeRewards(v);
            } catch (Exception e) {
                System.out.println("Bad events-list constructed.");
            }

            events.addAll(v);
        }


        RewardsManager simple = new SimpleRewardsManager();
        Map<Address, Double> r0 = simple.computeRewards(events);

        RewardsManager f1 = new F1ToyRewardsManager();
        Map<Address, Double> r1 = f1.computeRewards(events);

        System.out.println(r0);
        System.out.println(r1);
        double[] error1 = calcErrorSD(r0, r1);
        System.out.printf("Error (F1): mean = %.2f%%, sd = %.2f%%\n", error1[0], error1[1]);
    }

    private Address addressOf(int n) {
        byte[] b = new byte[32];

        byte[] trimmed = BigInteger.valueOf(n).toByteArray();
        int padding = 32 - trimmed.length;

        System.arraycopy(trimmed, 0, b, padding, trimmed.length);

        return new Address(b);
    }

    private int getRandomInt(int min, int max) {
        return (int) (Math.random() * max) + min;
    }

    private long getRandomLong(long min, long max) {
        return (long) (Math.random() * max) + min;
    }

    private boolean coinWithProbability(float probability) {
        return (new Random().nextFloat() < probability);
    }

    // assuming key sets are the same
    private double[] calcErrorSD(Map<Address, Double> base, Map<Address, Double> toCheck) {
        double[] errors = base.keySet().stream().mapToDouble(k -> {
            double v1 = base.getOrDefault(k, 0d);
            double v2 = toCheck.getOrDefault(k, 0d);
            return 100.0 * Math.abs(v2 - v1) / v1;
        }).toArray();


        double mean = DoubleStream.of(errors).sum() / errors.length;
        double sd = Math.sqrt(DoubleStream.of(errors).map(e -> Math.pow(e - mean, 2)).sum() / errors.length);

        return new double[]{mean, sd};
    }
}

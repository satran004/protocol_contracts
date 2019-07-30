package org.aion.unity;

import avm.Address;

import java.util.List;
import java.util.Map;

public abstract class RewardsManager {

    public enum EventType {
        VOTE, UNVOTE, WITHDRAW, BLOCK
    }

    @SuppressWarnings("WeakerAccess")
    public static class Event {
        public EventType type;
        public Address source;
        public long blockNumber;
        public double amount;

        public Event(EventType type, Address source, long blockNumber, double amount) {
            this.type = type;
            this.source = source;
            this.blockNumber = blockNumber;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return "Event{" +
                    "type=" + type +
                    ", source=" + source +
                    ", blockNumber=" + blockNumber +
                    ", amount=" + amount +
                    '}';
        }
    }

    /**
     * Compute the final rewards for all delegators.
     *
     * @param events a list of user operations
     * @return both pending and withdrawn rewards
     */
    public abstract Map<Address, Double> computeRewards(List<Event> events);
}

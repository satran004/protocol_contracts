package org.aion.unity.model;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class HistoricalRewardsStore {

    private Map<Long, HistoricalReward> map;

    public HistoricalRewardsStore() {
        map = new HashMap<>();
    }

    public void setHistoricalReward(long period, HistoricalReward reward) {

        if (map.containsKey(period))
            throw new RuntimeException("Attempted to over-write a historical record");

        map.put(period, reward);
    }

    public void incrementReferenceCount(long period) {
        HistoricalReward v = map.get(period);

        if (v == null)
            throw new RuntimeException("Incremented reference count on a historical record that does not exist");

        if (v.referenceCount >= 2)
            throw new RuntimeException("Attempted to increment reference count beyond 2");

        v.referenceCount++;
    }

    public void decrementReferenceCount(long period) {
        HistoricalReward v = map.get(period);

        if (v == null)
            throw new RuntimeException("Incremented reference count on a historical record that does not exist");

        if (v.referenceCount <= 0)
            throw new RuntimeException("Attempted to decrement reference count below 0");

        if (v.referenceCount-- <= 0) {
            map.remove(period);
        }
    }

    public HistoricalReward getHistoricalReward(long period) {
        HistoricalReward v = map.get(period);

        if (v == null)
            throw new RuntimeException("Attempted to retrieve a historical record that does not exist");

        return v;
    }

}
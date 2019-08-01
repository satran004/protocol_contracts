package org.aion.unity.toy.model;

/**
 * Stores some "starting information" for a delegation:
 * 1. Previous period (to track the new period this delegation created)
 * 2. How much stake was delegated
 * 3. Block number when stake was delegated
 */
@SuppressWarnings({"WeakerAccess"})
public class StartingInfo {
    public long previousPeriod;    // period at which the delegation should withdraw starting from
    public double stake;             // amount of coins being delegated
    public long blockNumber;       // block number at which delegation was created

    public StartingInfo(long previousPeriod, double stake, long blockNumber) {
        this.previousPeriod = previousPeriod;
        this.stake = stake;
        this.blockNumber = blockNumber;
    }
}

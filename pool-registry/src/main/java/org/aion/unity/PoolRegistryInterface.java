package org.aion.unity;

import avm.Address;

@SuppressWarnings({"unused", "UnnecessaryInterfaceModifier"})
public interface PoolRegistryInterface extends StakerRegistryListener {

    // Register a pool in the registry.
    public Address registerPool(byte[] metaData, int commissionRate);

    // Delegates stake to a pool.
    public void delegate(Address pool);

    // Cancels stake to a pool.
    public void undelegate(Address pool, long amount);

    // Redelegate a pool using the rewards.
    public void redelegateRewards(Address pool);

    // Transfers stake from one pool to another.
    public  void transferStake(Address fromPool, Address toPool, long amount);

    // Returns the stake of a delegator to a pool.
    public long getStake(Address pool, Address delegator);

    // Returns the outstanding rewards of a delegator.
    public long getRewards(Address pool, Address delegator);

    // Withdraws rewards from one pool
    public long withdraw(Address pool);

    // Returns pool status (ACTIVE or BROKEN).
    public String getPoolStatus(Address pool);
}

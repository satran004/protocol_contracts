package org.aion.unity;

import avm.Address;

@SuppressWarnings({"unused", "UnnecessaryInterfaceModifier"})
public interface PoolRegistryInterface extends StakerRegistryListener {

    // register a pool in this registry.
    public Address registerPool(byte[] metaData, int commissionRate);

    // delegate coins to a pool.
    public void delegate(Address pool);

    // convert stake (delegated to a pool) to liquid coins (un-stake lockout applies).
    public void undelegate(Address pool, long amount);

    // delegate rewards earned, back to the pool.
    public void redelegateRewards(Address pool);

    // transfer stake from one pool to another (transfer-stake lockout applies).
    public void transferStake(Address fromPool, Address toPool, long amount);

    // withdraws rewards (for delegated stake) from a pool.
    public long withdraw(Address pool);

    // finalize up to {@code limit} number of un-vote operations, for the given address; can be called by any user.
    public int finalizeUnvote(Address owner, int limit);

    // finalize up to {@code limit} transfer operations; can be called by any user.
    public void finalizeTransfer(long transferId);

    // enable auto re-delegation of rewards, specifying the "tip" callers of {@link #autoRedelegate(Address, Address) autoRedelegate} can earn.
    public void enableAutoRedelegation(Address pool, int feePercentage);

    // disable auto re-delegation of rewards.
    public void disableAutoRedelegation(Address pool);

    // delegates rewards for the given address, if delegator opted into  auto-redelegation; can be called by any user.
    public void autoRedelegate(Address pool, Address delegator);
}

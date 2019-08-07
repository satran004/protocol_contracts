package org.aion.unity;

import avm.Address;
/**
 * A staker registry manages the staker registration, and provides an interface for coin-holders
 * to vote/unvote for a staker.
 */
@SuppressWarnings({"WeakerAccess", "UnnecessaryInterfaceModifier", "unused"})
public interface StakerRegistryInterface {

    // Registers a staker. The caller address will be the identification address of a registered staker.
    public void registerStaker(Address signingAddress, Address coinbaseAddress);

    // Votes for a staker. Any liquid coins, passed along the call, become locked stake.
    public void vote(Address staker);

    // Unvotes for a staker. After a successful unvote, the locked coins will be released to the original owners, subject to lock-up period.
    public void unvote(Address staker, long amount);

    // Un-votes for a staker, and receives the released fund using another account.
    public void unvoteTo(Address staker, long amount, Address receiver);

    // Transfers stake from one staker to another staker.
    public void transferStake(Address fromStaker, Address toStaker, long amount);

    // Releases the stake (locked coin) to the owner.
    public int releaseStake(Address owner, int limit);

    // Setters
    // ----------------------------------------------------------------

    // Updates the signing address of a staker. Owner only.
     public void setSigningAddress(Address newSigningAddress);

    // Updates the coinbase address of a staker. Owner only.
    public void setCoinbaseAddress(Address newCoinbaseAddress);

    // Listeners
    // ----------------------------------------------------------------

    // Registers a listener. Owner only.
    public void addListener(Address listener);

    // De-registers a listener. Owner only.
    public void removeListener(Address listener);

    // Returns if the given listener is registered to the staker.
    public boolean isListener(Address staker, Address listener);

    // Getters
    // ----------------------------------------------------------------

    // Returns the total stake associated with a staker.
    public long getStakeByStakerAddress(Address staker);

    // Returns the total stake associated with a staker.
    public long getStakeBySigningAddress(Address staker);

    // Returns the stake from a voter to a staker.
    public long getStake(Address staker, Address voter);

    // Returns the signing address of a staker.
    public Address getSigningAddress(Address staker);

    // Returns the coinbase address of a staker.
    public Address getCoinbaseAddress(Address staker);

}

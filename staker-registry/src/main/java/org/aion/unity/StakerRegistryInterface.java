package org.aion.unity;

import avm.Address;
/**
 * A staker registry manages the staker registration, and provides an interface for coin-holders
 * to vote/unvote for a staker.
 */
@SuppressWarnings({"WeakerAccess", "UnnecessaryInterfaceModifier", "unused"})
public interface StakerRegistryInterface {

    // registers a staker; the caller address will be the identification address of a registered staker.
    public void registerStaker(Address signingAddress, Address coinbaseAddress);

    // votes for a staker; any liquid coins, passed along the call, become locked stake.
    public void vote(Address staker);

    // un-votes for a staker; after a successful unvote, the locked coins will be released to the original owners, subject to lock-up period.
    public void unvote(Address staker, long amount);

    // un-votes for a staker, and receives the released fund using another account.
    public void unvoteTo(Address staker, long amount, Address receiver);

    // releases stake (locked coin) to owner (after un-stake lockout has elapsed).
    public int finalizeUnvote(Address owner, int limit);

    // transfers stake from one staker to another staker.
    public void transferStake(Address fromStaker, Address toStaker, long amount);

    // finalize up to {@code limit} number of un-vote operations, for the given address.
    public int finalizeTransfer(Address staker, int limit);

    // updates the signing address of a staker; can be called by owner only.
     public void setSigningAddress(Address newSigningAddress);

    // updates the coinbase address of a staker; can be called by owner only.
    public void setCoinbaseAddress(Address newCoinbaseAddress);

    // registers a listener; can be called by owner only.
    public void addListener(Address listener);

    // de-registers a listener; can be called by owner only.
    public void removeListener(Address listener);
}

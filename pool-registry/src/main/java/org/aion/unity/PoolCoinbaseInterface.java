package org.aion.unity;

import avm.Address;

@SuppressWarnings({"UnnecessaryInterfaceModifier", "unused"})
public interface PoolCoinbaseInterface {

    // Transfer amount to recipient (restricted to the poolRegistry contract)
    public void transfer(Address recipient, long amount);
}
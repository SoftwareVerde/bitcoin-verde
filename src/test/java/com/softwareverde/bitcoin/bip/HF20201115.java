package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public class HF20201115 {
    protected static final Long MAIN_NET_BLOCK_TIME = 1605441600L;
    protected static final Long TEST_NET_BLOCK_TIME = 1603393200L;

    public static Long ACTIVATION_BLOCK_TIME = MAIN_NET_BLOCK_TIME;

    // Bitcoin Cash: 2020-11-15 Hard Fork:  https://gitlab.com/bitcoin-cash-node/bchn-sw/bitcoincash-upgrade-specifications/-/blob/master/spec/2020-11-15-upgrade.md
    public static Boolean isEnabled(final MedianBlockTime medianBlockTime) {
        if (medianBlockTime == null) { return true; }

        final long currentTimestamp = medianBlockTime.getCurrentTimeInSeconds();
        return (currentTimestamp >= ACTIVATION_BLOCK_TIME);
    }

    public static void enableTestNet() {
        ACTIVATION_BLOCK_TIME = TEST_NET_BLOCK_TIME;
    }

    public static void enableMainNet() {
        ACTIVATION_BLOCK_TIME = MAIN_NET_BLOCK_TIME;
    }

    protected HF20201115() { }
}

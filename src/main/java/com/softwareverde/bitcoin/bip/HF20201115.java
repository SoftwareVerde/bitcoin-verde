package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

class HF20201115 {
    public static final Long ACTIVATION_BLOCK_TIME = 1605441600L;

    // Bitcoin Cash: 2020-11-15 Hard Fork:  https://gitlab.com/bitcoin-cash-node/bchn-sw/bitcoincash-upgrade-specifications/-/blob/master/spec/2020-11-15-upgrade.md
    public static Boolean isEnabled(final MedianBlockTime medianBlockTime) {
        if (medianBlockTime == null) { return true; }

        final long currentTimestamp = medianBlockTime.getCurrentTimeInSeconds();
        return (currentTimestamp >= ACTIVATION_BLOCK_TIME);
    }

    protected HF20201115() { }
}

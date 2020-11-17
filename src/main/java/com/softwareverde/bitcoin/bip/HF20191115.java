package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

class HF20191115 {
    public static final Long ACTIVATION_BLOCK_TIME = 1573819200L;
    public static final Long ACTIVATION_BLOCK_HEIGHT = 609134L;

    // Bitcoin Cash: 2019-11-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-11-15-upgrade.md
    public static Boolean isEnabled(final MedianBlockTime medianBlockTime) {
        if (medianBlockTime == null) { return true; }

        final long currentTimestamp = medianBlockTime.getCurrentTimeInSeconds();
        return (currentTimestamp >= ACTIVATION_BLOCK_TIME);
    }

    protected HF20191115() { }
}

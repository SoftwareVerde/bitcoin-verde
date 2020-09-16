package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

class HF20200515 {
    public static final Long ACTIVATION_BLOCK_TIME = 1589544000L;
    public static final Long ACTIVATION_BLOCK_HEIGHT = 635257L;

    // Bitcoin Cash: 2020-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2020-05-15-upgrade.md
    public static Boolean isEnabled(final MedianBlockTime medianBlockTime) {
        if (medianBlockTime == null) { return true; }

        final long currentTimestamp = medianBlockTime.getCurrentTimeInSeconds();
        return (currentTimestamp >= ACTIVATION_BLOCK_TIME);
    }

    protected HF20200515() { }
}

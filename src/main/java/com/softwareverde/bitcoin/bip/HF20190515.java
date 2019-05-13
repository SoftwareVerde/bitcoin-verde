package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public class HF20190515 {
    // Bitcoin Cash: 2019-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-05-15-upgrade.md

    public static Boolean isEnabled(final MedianBlockTime medianBlockTime) {
        if (medianBlockTime == null) { return true; }

        final long currentTimestamp = medianBlockTime.getCurrentTimeInSeconds();
        return (currentTimestamp >= 1557921600L);
    }

    protected HF20190515() { }
}

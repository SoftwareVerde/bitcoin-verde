package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public class ChipNetUpgradeSchedule extends TestNet4UpgradeSchedule {
    private static final long HF20230515_ACTIVATION_TIME = 1668513600L; // September: 1662250000L (TempNet)

    @Override
    public Boolean areTransactionsLessThanSixtyFiveBytesDisallowed(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= HF20230515_ACTIVATION_TIME);
    }

    @Override
    public Boolean areTransactionVersionsRestricted(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= HF20230515_ACTIVATION_TIME);
    }
}

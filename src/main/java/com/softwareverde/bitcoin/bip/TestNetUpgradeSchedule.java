package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public class TestNetUpgradeSchedule implements UpgradeSchedule {
    @Override
    public Boolean isMinimalNumberEncodingRequired(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= 1573820238L);
    }

    @Override
    public Boolean isBitcoinCashSignatureHashTypeEnabled(final Long blockHeight) {
        return (blockHeight >= 1155875L);
    }

    @Override
    public Boolean areOnlyPushOperationsAllowedWithinUnlockingScript(final Long blockHeight) {
        return (blockHeight >= 1267996L);
    }

    @Override
    public Boolean isPayToScriptHashEnabled(final Long blockHeight) {
        return (blockHeight >= 514L);
    }

    @Override
    public Boolean isAsertDifficultyAdjustmentAlgorithmEnabled(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= 1605441600L);
    }

    @Override
    public Boolean isCw144DifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return (blockHeight >= 1188697L);
    }

    @Override
    public Boolean isEmergencyDifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return (blockHeight >= 1155875L);
    }

    @Override
    public Boolean isBlockHeightWithinCoinbaseRequired(final Long blockHeight) {
        return (blockHeight >= 21111L);
    }

    @Override
    public Boolean isRelativeLockTimeEnabled(final Long blockHeight) {
        return (blockHeight >= 770112L);
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForTransactionLockTime(final Long blockHeight) {
        return (blockHeight >= 770112L);
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForBlockTimestamp(final Long blockHeight) {
        return (blockHeight >= 770112L);
    }

    @Override
    public Boolean isCheckLockTimeOperationEnabled(final Long blockHeight) {
        return (blockHeight >= 581885L);
    }

    @Override
    public Boolean isCheckSequenceNumberOperationEnabled(final Long blockHeight) {
        return (blockHeight >= 770112);
    }

    @Override
    public Boolean areAllInvalidSignaturesRequiredToBeEmpty(final Long blockHeight) {
        return (blockHeight >= 1188697L);
    }

    @Override
    public Boolean areCanonicalSignatureEncodingsRequired(final Long blockHeight) {
        return (blockHeight >= 1188697L);
    }

    @Override
    public Boolean areSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return (blockHeight >= 1155875L);
    }

    @Override
    public Boolean arePublicKeysRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return (blockHeight >= 1155875L);
    }

    @Override
    public Boolean areDerSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return (blockHeight >= 330776L);
    }

    @Override
    public Boolean areUnusedValuesAfterScriptExecutionDisallowed(final Long blockHeight) {
        return (blockHeight >= 1267996L);
    }

    @Override
    public Boolean areTransactionsLessThanOneHundredBytesDisallowed(final Long blockHeight) {
        return (blockHeight >= 1267996L);
    }

    @Override
    public Boolean areUnusedValuesAfterSegwitScriptExecutionAllowed(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= 1542300039L); // defined as 1267996 in BCHN
    }

    @Override
    public Boolean isSignatureOperationCountingVersionTwoEnabled(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= 1605441600L);
    }

    @Override
    public Boolean isCheckDataSignatureOperationEnabled(final Long blockHeight) {
        return (blockHeight >= 1267996L);
    }

    @Override
    public Boolean areSchnorrSignaturesEnabledWithinMultiSignature(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= 1573820238L);
    }

    @Override
    public Boolean isReverseBytesOperationEnabled(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= 1605441600L);
    }
}

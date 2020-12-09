package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public class CoreUpgradeSchedule implements UpgradeSchedule {
    @Override
    public Boolean isMinimalNumberEncodingRequired(final MedianBlockTime medianBlockTime) {
        return HF20191115.isEnabled(medianBlockTime);
    }

    @Override
    public Boolean isBitcoinCashSignatureHashTypeEnabled(final Long blockHeight) {
        return Buip55.isEnabled(blockHeight);
    }

    @Override
    public Boolean areOnlyPushOperationsAllowedWithinUnlockingScript(final Long blockHeight) {
        return HF20181115.isEnabled(blockHeight);
    }

    @Override
    public Boolean isPayToScriptHashEnabled(final Long blockHeight) {
        return Bip16.isEnabled(blockHeight);
    }

    @Override
    public Boolean isAsertDifficultyAdjustmentAlgorithmEnabled(final MedianBlockTime medianBlockTime) {
        return HF20201115.isEnabled(medianBlockTime);
    }

    @Override
    public Boolean isCw144DifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return HF20171113.isEnabled(blockHeight);
    }

    @Override
    public Boolean isEmergencyDifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return Buip55.isEnabled(blockHeight);
    }

    @Override
    public Boolean isBlockHeightWithinCoinbaseRequired(final Long blockHeight) {
        return Bip34.isEnabled(blockHeight);
    }

    @Override
    public Boolean isRelativeLockTimeEnabled(final Long blockHeight) {
        return Bip68.isEnabled(blockHeight);
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForTransactionLockTime(final Long blockHeight) {
        return Bip113.isEnabled(blockHeight);
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForBlockTimestamp(final Long blockHeight) {
        return Bip113.isEnabled(blockHeight);
    }

    @Override
    public Boolean isCheckLockTimeOperationEnabled(final Long blockHeight) {
        return Bip65.isEnabled(blockHeight);
    }

    @Override
    public Boolean isCheckSequenceNumberOperationEnabled(final Long blockHeight) {
        return Bip112.isEnabled(blockHeight);
    }

    @Override
    public Boolean areAllInvalidSignaturesRequiredToBeEmpty(final Long blockHeight) {
        return HF20171113.isEnabled(blockHeight);
    }

    @Override
    public Boolean areSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return Buip55.isEnabled(blockHeight);
    }

    @Override
    public Boolean areCanonicalSignatureEncodingsRequired(final Long blockHeight) {
        return HF20171113.isEnabled(blockHeight);
    }

    @Override
    public Boolean arePublicKeysRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return Buip55.isEnabled(blockHeight);
    }

    @Override
    public Boolean areDerSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return Bip66.isEnabled(blockHeight);
    }

    @Override
    public Boolean areUnusedValuesAfterScriptExecutionDisallowed(final Long blockHeight) {
        return HF20181115.isEnabled(blockHeight);
    }

    @Override
    public Boolean areTransactionsLessThanOneHundredBytesDisallowed(final Long blockHeight) {
        return HF20181115.isEnabled(blockHeight);
    }

    @Override
    public Boolean areUnusedValuesAfterSegwitScriptExecutionAllowed(final MedianBlockTime medianBlockTime) {
        return HF20190515.isEnabled(medianBlockTime);
    }

    @Override
    public Boolean isSignatureOperationCountingVersionTwoEnabled(final MedianBlockTime medianBlockTime) {
        return HF20200515.isEnabled(medianBlockTime);
    }

    @Override
    public Boolean areCanonicalMultiSignatureCheckEncodingsRequired(final MedianBlockTime medianBlockTime) {
        return HF20191115.isEnabled(medianBlockTime);
    }

    @Override
    public Boolean isCheckDataSignatureOperationEnabled(final Long blockHeight) {
        return HF20181115.isEnabled(blockHeight);
    }

    @Override
    public Boolean areSchnorrSignaturesEnabledWithinMultiSignature(final MedianBlockTime medianBlockTime) {
        return HF20191115.isEnabled(medianBlockTime);
    }

    @Override
    public Boolean isReverseBytesOperationEnabled(final MedianBlockTime medianBlockTime) {
        return HF20200515.isEnabled(medianBlockTime);
    }
}

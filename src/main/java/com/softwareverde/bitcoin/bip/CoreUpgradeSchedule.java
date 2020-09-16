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
    public Boolean disallowNonPushOperationsWithinUnlockingScript(final Long blockHeight) {
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
    public Boolean requireBlockHeightWithinCoinbase(final Long blockHeight) {
        return Bip34.isEnabled(blockHeight);
    }

    @Override
    public Boolean isRelativeLockTimeEnabled(final Long blockHeight) {
        return Bip68.isEnabled(blockHeight);
    }

    @Override
    public Boolean enableMedianBlockTimeForTransactionLockTime(final Long blockHeight) {
        return Bip113.isEnabled(blockHeight);
    }

    @Override
    public Boolean enableMedianBlockTimeForBlockTimestamp(final Long blockHeight) {
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
    public Boolean requireAllInvalidSignaturesBeEmpty(final Long blockHeight) {
        return HF20171113.isEnabled(blockHeight);
    }

    @Override
    public Boolean requireStrictSignatureAndPublicKeyEncoding(final Long blockHeight) {
        return Buip55.isEnabled(blockHeight);
    }

    @Override
    public Boolean requireCanonicalSignatureEncoding(final Long blockHeight) {
        return HF20171113.isEnabled(blockHeight);
    }

    @Override
    public Boolean requireStrictPublicKeyEncoding(final Long blockHeight) {
        return Buip55.isEnabled(blockHeight);
    }

    @Override
    public Boolean disallowNegativeDerSignatureEncodings(final Long blockHeight) {
        return Bip66.isEnabled(blockHeight);
    }

    @Override
    public Boolean disallowUnusedValuesAfterScriptExecution(final Long blockHeight) {
        return HF20181115.isEnabled(blockHeight);
    }

    @Override
    public Boolean enableMinimumTransactionByteCount(final Long blockHeight) {
        return HF20181115.isEnabled(blockHeight);
    }

    @Override
    public Boolean allowUnusedValuesAfterScriptExecutionForSegwitScripts(final MedianBlockTime medianBlockTime) {
        return HF20190515.isEnabled(medianBlockTime);
    }

    @Override
    public Boolean enableSignatureOperationCountingVersion2(final MedianBlockTime medianBlockTime) {
        return HF20200515.isEnabled(medianBlockTime);
    }

    @Override
    public Boolean requireMultiSignatureChecksAreCanonicallyEncoded(final MedianBlockTime medianBlockTime) {
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

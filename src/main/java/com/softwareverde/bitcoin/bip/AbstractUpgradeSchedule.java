package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public abstract class AbstractUpgradeSchedule implements UpgradeSchedule {
    protected long[] _activationBlockHeights = new long[UpgradeHeight.values().length];
    protected long[] _activationBlockTimes = new long[UpgradeTime.values().length];

    protected Boolean _isActive(final Long blockHeight, final UpgradeHeight upgradeHeight) {
        final long activationHeight = _activationBlockHeights[upgradeHeight.value];
        return (blockHeight >= activationHeight);
    }

    protected Boolean _isActive(final MedianBlockTime medianBlockTime, final UpgradeTime upgradeTime) {
        final long medianBlockTimeSeconds = medianBlockTime.getCurrentTimeInSeconds();
        final long activationTime = _activationBlockTimes[upgradeTime.value];
        return (medianBlockTimeSeconds >= activationTime);
    }

    protected AbstractUpgradeSchedule() { }

    @Override
    public Boolean didUpgradeActivate(final Long blockHeight0, final MedianBlockTime medianBlockTime0, final Long blockHeight1, final MedianBlockTime medianBlockTime1) {
        for (final UpgradeHeight upgradeHeight : UpgradeHeight.values()) {
            final boolean hasChange = (_isActive(blockHeight0, upgradeHeight) ^ _isActive(blockHeight1, upgradeHeight));
            if (hasChange) { return true; }
        }

        for (final UpgradeTime upgradeTime : UpgradeTime.values()) {
            final boolean hasChange = (_isActive(medianBlockTime0, upgradeTime) ^ _isActive(medianBlockTime1, upgradeTime));
            if (hasChange) { return true; }
        }

        return false;
    }

    @Override
    public Boolean isMinimalNumberEncodingRequired(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20191115_ACTIVATION_TIME);
    }

    @Override
    public Boolean isBitcoinCashSignatureHashTypeEnabled(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BUIP55_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areOnlyPushOperationsAllowedWithinUnlockingScript(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.HF20181115_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isLegacyPayToScriptHashEnabled(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BIP16_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isSha256PayToScriptHashEnabled(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20230515_ACTIVATION_TIME);
    }

    @Override
    public Boolean isAsertDifficultyAdjustmentAlgorithmEnabled(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20201115_ACTIVATION_TIME);
    }

    @Override
    public Boolean isCw144DifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.HF20171113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isEmergencyDifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BUIP55_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isBlockHeightWithinCoinbaseRequired(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BIP34_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isRelativeLockTimeEnabled(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BIP68_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForTransactionLockTime(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BIP113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForBlockTimestamp(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BIP113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isCheckLockTimeOperationEnabled(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BIP65_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isCheckSequenceNumberOperationEnabled(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BIP112_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areAllInvalidSignaturesRequiredToBeEmpty(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.HF20171113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BUIP55_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areCanonicalSignatureEncodingsRequired(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.HF20171113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean arePublicKeysRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BUIP55_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areDerSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.BIP66_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areUnusedValuesAfterScriptExecutionDisallowed(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.HF20181115_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areTransactionsLessThanOneHundredBytesDisallowed(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.HF20181115_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areTransactionsLessThanSixtyFiveBytesDisallowed(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20230515_ACTIVATION_TIME);
    }

    @Override
    public Boolean areUnusedValuesAfterSegwitScriptExecutionAllowed(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20190515_ACTIVATION_TIME);
    }


    @Override
    public Boolean isSignatureOperationCountingVersionTwoEnabled(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20200515_ACTIVATION_TIME);
    }

    @Override
    public Boolean isCheckDataSignatureOperationEnabled(final Long blockHeight) {
        return _isActive(blockHeight, UpgradeHeight.HF20181115_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areSchnorrSignaturesEnabledWithinMultiSignature(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20191115_ACTIVATION_TIME);
    }

    @Override
    public Boolean isReverseBytesOperationEnabled(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20200515_ACTIVATION_TIME);
    }

    @Override
    public Boolean areIntrospectionOperationsEnabled(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20220515_ACTIVATION_TIME);
    }

    @Override
    public Boolean are64BitScriptIntegersEnabled(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20220515_ACTIVATION_TIME);
    }

    @Override
    public Boolean isMultiplyOperationEnabled(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20220515_ACTIVATION_TIME);
    }

    @Override
    public Boolean areTransactionVersionsRestricted(final MedianBlockTime medianBlockTime) {
        return _isActive(medianBlockTime, UpgradeTime.HF20230515_ACTIVATION_TIME);
    }
}

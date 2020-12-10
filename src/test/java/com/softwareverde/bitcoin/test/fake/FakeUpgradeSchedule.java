package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

/**
 * Wraps an UpgradeSchedule object which is used unless a relevant "set" method was called to enable/disable a certain feature.
 */
public class FakeUpgradeSchedule implements UpgradeSchedule {
    protected final UpgradeSchedule _coreUpgradeSchedule;
    protected Boolean _isMinimalNumberEncodingRequired;
    protected Boolean _isBitcoinCashSignatureHashTypeEnabled;
    protected Boolean _areOnlyPushOperationsAllowedWithinUnlockingScript;
    protected Boolean _isPayToScriptHashEnabled;
    protected Boolean _isAsertDifficultyAdjustmentAlgorithmEnabled;
    protected Boolean _isCw144DifficultyAdjustmentAlgorithmEnabled;
    protected Boolean _isEmergencyDifficultyAdjustmentAlgorithmEnabled;
    protected Boolean _isBlockHeightWithinCoinbaseRequired;
    protected Boolean _isRelativeLockTimeEnabled;
    protected Boolean _shouldUseMedianBlockTimeForTransactionLockTime;
    protected Boolean _shouldUseMedianBlockTimeForBlockTimestamp;
    protected Boolean _isCheckLockTimeOperationEnabled;
    protected Boolean _isCheckSequenceNumberOperationEnabled;
    protected Boolean _areAllInvalidSignaturesRequiredToBeEmpty;
    protected Boolean _areCanonicalSignatureEncodingsRequired;
    protected Boolean _areSignaturesRequiredToBeStrictlyEncoded;
    protected Boolean _arePublicKeysRequiredToBeStrictlyEncoded;
    protected Boolean _areDerSignaturesRequiredToBeStrictlyEncoded;
    protected Boolean _areUnusedValuesAfterScriptExecutionDisallowed;
    protected Boolean _areTransactionsLessThanOneHundredBytesDisallowed;
    protected Boolean _areUnusedValuesAfterSegwitScriptExecutionAllowed;
    protected Boolean _isSignatureOperationCountingVersionTwoEnabled;
    protected Boolean _areCanonicalMultiSignatureCheckEncodingsRequired;
    protected Boolean _isCheckDataSignatureOperationEnabled;
    protected Boolean _areSchnorrSignaturesEnabledWithinMultiSignature;
    protected Boolean _isReverseBytesOperationEnabled;

    public FakeUpgradeSchedule(final UpgradeSchedule upgradeSchedule) {
        _coreUpgradeSchedule = upgradeSchedule;
    }

    protected Boolean _isOverriddenOrEnabled(final Boolean override, final Boolean isEnabled) {
        return (override != null) ? override : isEnabled;
    }

    @Override
    public Boolean isMinimalNumberEncodingRequired(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _isMinimalNumberEncodingRequired,
                _coreUpgradeSchedule.isMinimalNumberEncodingRequired(medianBlockTime)
        );
    }

    @Override
    public Boolean isBitcoinCashSignatureHashTypeEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isBitcoinCashSignatureHashTypeEnabled,
                _coreUpgradeSchedule.isBitcoinCashSignatureHashTypeEnabled(blockHeight)
        );
    }

    @Override
    public Boolean areOnlyPushOperationsAllowedWithinUnlockingScript(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areOnlyPushOperationsAllowedWithinUnlockingScript,
                _coreUpgradeSchedule.areOnlyPushOperationsAllowedWithinUnlockingScript(blockHeight)
        );
    }

    @Override
    public Boolean isPayToScriptHashEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isPayToScriptHashEnabled,
                _coreUpgradeSchedule.isPayToScriptHashEnabled(blockHeight)
        );
    }

    @Override
    public Boolean isAsertDifficultyAdjustmentAlgorithmEnabled(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _isAsertDifficultyAdjustmentAlgorithmEnabled,
                _coreUpgradeSchedule.isAsertDifficultyAdjustmentAlgorithmEnabled(medianBlockTime)
        );
    }

    @Override
    public Boolean isCw144DifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isCw144DifficultyAdjustmentAlgorithmEnabled,
                _coreUpgradeSchedule.isCw144DifficultyAdjustmentAlgorithmEnabled(blockHeight)
        );
    }

    @Override
    public Boolean isEmergencyDifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isEmergencyDifficultyAdjustmentAlgorithmEnabled,
                _coreUpgradeSchedule.isEmergencyDifficultyAdjustmentAlgorithmEnabled(blockHeight)
        );
    }

    @Override
    public Boolean isBlockHeightWithinCoinbaseRequired(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isBlockHeightWithinCoinbaseRequired,
                _coreUpgradeSchedule.isBlockHeightWithinCoinbaseRequired(blockHeight)
        );
    }

    @Override
    public Boolean isRelativeLockTimeEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isRelativeLockTimeEnabled,
                _coreUpgradeSchedule.isRelativeLockTimeEnabled(blockHeight)
        );
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForTransactionLockTime(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _shouldUseMedianBlockTimeForTransactionLockTime,
                _coreUpgradeSchedule.shouldUseMedianBlockTimeForTransactionLockTime(blockHeight)
        );
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForBlockTimestamp(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _shouldUseMedianBlockTimeForBlockTimestamp,
                _coreUpgradeSchedule.shouldUseMedianBlockTimeForBlockTimestamp(blockHeight)
        );
    }

    @Override
    public Boolean isCheckLockTimeOperationEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isCheckLockTimeOperationEnabled,
                _coreUpgradeSchedule.isCheckLockTimeOperationEnabled(blockHeight)
        );
    }

    @Override
    public Boolean isCheckSequenceNumberOperationEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isCheckSequenceNumberOperationEnabled,
                _coreUpgradeSchedule.isCheckSequenceNumberOperationEnabled(blockHeight)
        );
    }

    @Override
    public Boolean areAllInvalidSignaturesRequiredToBeEmpty(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areAllInvalidSignaturesRequiredToBeEmpty,
                _coreUpgradeSchedule.areAllInvalidSignaturesRequiredToBeEmpty(blockHeight)
        );
    }

    @Override
    public Boolean areCanonicalSignatureEncodingsRequired(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areCanonicalSignatureEncodingsRequired,
                _coreUpgradeSchedule.areCanonicalSignatureEncodingsRequired(blockHeight)
        );
    }

    @Override
    public Boolean areSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areSignaturesRequiredToBeStrictlyEncoded,
                _coreUpgradeSchedule.areSignaturesRequiredToBeStrictlyEncoded(blockHeight)
        );
    }

    @Override
    public Boolean arePublicKeysRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _arePublicKeysRequiredToBeStrictlyEncoded,
                _coreUpgradeSchedule.arePublicKeysRequiredToBeStrictlyEncoded(blockHeight)
        );
    }

    @Override
    public Boolean areDerSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areDerSignaturesRequiredToBeStrictlyEncoded,
                _coreUpgradeSchedule.areDerSignaturesRequiredToBeStrictlyEncoded(blockHeight)
        );
    }

    @Override
    public Boolean areUnusedValuesAfterScriptExecutionDisallowed(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areUnusedValuesAfterScriptExecutionDisallowed,
                _coreUpgradeSchedule.areUnusedValuesAfterScriptExecutionDisallowed(blockHeight)
        );
    }

    @Override
    public Boolean areTransactionsLessThanOneHundredBytesDisallowed(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areTransactionsLessThanOneHundredBytesDisallowed,
                _coreUpgradeSchedule.areTransactionsLessThanOneHundredBytesDisallowed(blockHeight)
        );
    }

    @Override
    public Boolean areUnusedValuesAfterSegwitScriptExecutionAllowed(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _areUnusedValuesAfterSegwitScriptExecutionAllowed,
                _coreUpgradeSchedule.areUnusedValuesAfterSegwitScriptExecutionAllowed(medianBlockTime)
        );
    }

    @Override
    public Boolean isSignatureOperationCountingVersionTwoEnabled(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _isSignatureOperationCountingVersionTwoEnabled,
                _coreUpgradeSchedule.isSignatureOperationCountingVersionTwoEnabled(medianBlockTime)
        );
    }

    @Override
    public Boolean areCanonicalMultiSignatureCheckEncodingsRequired(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _areCanonicalMultiSignatureCheckEncodingsRequired,
                _coreUpgradeSchedule.areCanonicalMultiSignatureCheckEncodingsRequired(medianBlockTime)
        );
    }

    @Override
    public Boolean isCheckDataSignatureOperationEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isCheckDataSignatureOperationEnabled,
                _coreUpgradeSchedule.isCheckDataSignatureOperationEnabled(blockHeight)
        );
    }

    @Override
    public Boolean areSchnorrSignaturesEnabledWithinMultiSignature(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _areSchnorrSignaturesEnabledWithinMultiSignature,
                _coreUpgradeSchedule.areSchnorrSignaturesEnabledWithinMultiSignature(medianBlockTime)
        );
    }

    @Override
    public Boolean isReverseBytesOperationEnabled(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _isReverseBytesOperationEnabled,
                _coreUpgradeSchedule.isReverseBytesOperationEnabled(medianBlockTime)
        );
    }

    public void setMinimalNumberEncodingRequired(final Boolean minimalNumberEncodingRequired) {
        _isMinimalNumberEncodingRequired = minimalNumberEncodingRequired;
    }

    public void setBitcoinCashSignatureHashTypeEnabled(final Boolean bitcoinCashSignatureHashTypeEnabled) {
        _isBitcoinCashSignatureHashTypeEnabled = bitcoinCashSignatureHashTypeEnabled;
    }

    public void setOnlyPushOperationsAllowedWithinUnlockingScript(final Boolean areOnlyPushOperationsAllowedWithinUnlockingScript) {
        _areOnlyPushOperationsAllowedWithinUnlockingScript = areOnlyPushOperationsAllowedWithinUnlockingScript;
    }

    public void setPayToScriptHashEnabled(final Boolean payToScriptHashEnabled) {
        _isPayToScriptHashEnabled = payToScriptHashEnabled;
    }

    public void setAsertDifficultyAdjustmentAlgorithmEnabled(final Boolean asertDifficultyAdjustmentAlgorithmEnabled) {
        _isAsertDifficultyAdjustmentAlgorithmEnabled = asertDifficultyAdjustmentAlgorithmEnabled;
    }

    public void setCw144DifficultyAdjustmentAlgorithmEnabled(final Boolean cw144DifficultyAdjustmentAlgorithmEnabled) {
        _isCw144DifficultyAdjustmentAlgorithmEnabled = cw144DifficultyAdjustmentAlgorithmEnabled;
    }

    public void setEmergencyDifficultyAdjustmentAlgorithmEnabled(final Boolean emergencyDifficultyAdjustmentAlgorithmEnabled) {
        _isEmergencyDifficultyAdjustmentAlgorithmEnabled = emergencyDifficultyAdjustmentAlgorithmEnabled;
    }

    public void setBlockHeightWithinCoinbaseRequired(final Boolean blockHeightWithinCoinbaseRequired) {
        _isBlockHeightWithinCoinbaseRequired = blockHeightWithinCoinbaseRequired;
    }

    public void setRelativeLockTimeEnabled(final Boolean relativeLockTimeEnabled) {
        _isRelativeLockTimeEnabled = relativeLockTimeEnabled;
    }

    public void setShouldUseMedianBlockTimeForTransactionLockTime(final Boolean shouldUseMedianBlockTimeForTransactionLockTime) {
        _shouldUseMedianBlockTimeForTransactionLockTime = shouldUseMedianBlockTimeForTransactionLockTime;
    }

    public void setShouldUseMedianBlockTimeForBlockTimestamp(final Boolean shouldUseMedianBlockTimeForBlockTimestamp) {
        _shouldUseMedianBlockTimeForBlockTimestamp = shouldUseMedianBlockTimeForBlockTimestamp;
    }

    public void setCheckLockTimeOperationEnabled(final Boolean checkLockTimeOperationEnabled) {
        _isCheckLockTimeOperationEnabled = checkLockTimeOperationEnabled;
    }

    public void setCheckSequenceNumberOperationEnabled(final Boolean checkSequenceNumberOperationEnabled) {
        _isCheckSequenceNumberOperationEnabled = checkSequenceNumberOperationEnabled;
    }

    public void setAllInvalidSignaturesRequiredToBeEmpty(final Boolean areAllInvalidSignaturesRequiredToBeEmpty) {
        _areAllInvalidSignaturesRequiredToBeEmpty = areAllInvalidSignaturesRequiredToBeEmpty;
    }

    public void setCanonicalSignatureEncodingsRequired(final Boolean areCanonicalSignatureEncodingsRequired) {
        _areCanonicalSignatureEncodingsRequired = areCanonicalSignatureEncodingsRequired;
    }

    public void setSignaturesRequiredToBeStrictlyEncoded(final Boolean areSignaturesRequiredToBeStrictlyEncoded) {
        _areSignaturesRequiredToBeStrictlyEncoded = areSignaturesRequiredToBeStrictlyEncoded;
    }

    public void setPublicKeysRequiredToBeStrictlyEncoded(final Boolean arePublicKeysRequiredToBeStrictlyEncoded) {
        _arePublicKeysRequiredToBeStrictlyEncoded = arePublicKeysRequiredToBeStrictlyEncoded;
    }

    public void setDerSignaturesRequiredToBeStrictlyEncoded(final Boolean areNegativeDerSignatureEncodingsDisallowed) {
        _areDerSignaturesRequiredToBeStrictlyEncoded = areNegativeDerSignatureEncodingsDisallowed;
    }

    public void setUnusedValuesAfterScriptExecutionDisallowed(final Boolean areUnusedValuesAfterScriptExecutionDisallowed) {
        _areUnusedValuesAfterScriptExecutionDisallowed = areUnusedValuesAfterScriptExecutionDisallowed;
    }

    public void setTransactionsLessThanOneHundredBytesDisallowed(final Boolean areTransactionsLessThanOneHundredBytesDisallowed) {
        _areTransactionsLessThanOneHundredBytesDisallowed = areTransactionsLessThanOneHundredBytesDisallowed;
    }

    public void setUnusedValuesAfterSegwitScriptExecutionAllowed(final Boolean areUnusedValuesAfterSegwitScriptExecutionAllowed) {
        _areUnusedValuesAfterSegwitScriptExecutionAllowed = areUnusedValuesAfterSegwitScriptExecutionAllowed;
    }

    public void setSignatureOperationCountingVersionTwoEnabled(final Boolean signatureOperationCountingVersionTwoEnabled) {
        _isSignatureOperationCountingVersionTwoEnabled = signatureOperationCountingVersionTwoEnabled;
    }

    public void setCanonicalMultiSignatureCheckEncodingsRequired(final Boolean areCanonicalMultiSignatureCheckEncodingsRequired) {
        _areCanonicalMultiSignatureCheckEncodingsRequired = areCanonicalMultiSignatureCheckEncodingsRequired;
    }

    public void setCheckDataSignatureOperationEnabled(final Boolean checkDataSignatureOperationEnabled) {
        _isCheckDataSignatureOperationEnabled = checkDataSignatureOperationEnabled;
    }

    public void setAreSchnorrSignaturesEnabledWithinMultiSignature(final Boolean areSchnorrSignaturesEnabledWithinMultiSignature) {
        _areSchnorrSignaturesEnabledWithinMultiSignature = areSchnorrSignaturesEnabledWithinMultiSignature;
    }

    public void setReverseBytesOperationEnabled(final Boolean reverseBytesOperationEnabled) {
        _isReverseBytesOperationEnabled = reverseBytesOperationEnabled;
    }
}

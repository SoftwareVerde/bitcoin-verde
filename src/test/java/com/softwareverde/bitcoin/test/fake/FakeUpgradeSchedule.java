package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.util.Util;

/**
 * Wraps an UpgradeSchedule object which is used unless a relevant "set" method was called to enable/disable a certain feature.
 *
 * For consistency with the historical upgrade implementations, this also ensures that if a value that would be
 * passed into the parent upgrade schedule is null, it is converted to the highest value possible, to ensure it is enabled.
 * Note that if the value is set explicitly, that value will be returned.  It is only in the unset case that the parent
 * upgrade schedule value is used.
 */
public class FakeUpgradeSchedule implements UpgradeSchedule {
    protected final UpgradeSchedule _parentUpgradeSchedule;
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
    protected Boolean _isCheckDataSignatureOperationEnabled;
    protected Boolean _areSchnorrSignaturesEnabledWithinMultiSignature;
    protected Boolean _isReverseBytesOperationEnabled;

    public FakeUpgradeSchedule(final UpgradeSchedule upgradeSchedule) {
        _parentUpgradeSchedule = upgradeSchedule;
    }

    protected Boolean _isOverriddenOrEnabled(final Boolean override, final Boolean isEnabled) {
        return (override != null) ? override : isEnabled;
    }

    @Override
    public Boolean isMinimalNumberEncodingRequired(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _isMinimalNumberEncodingRequired,
                _parentUpgradeSchedule.isMinimalNumberEncodingRequired(Util.coalesce(medianBlockTime, MedianBlockTime.MAX_VALUE))
        );
    }

    @Override
    public Boolean isBitcoinCashSignatureHashTypeEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isBitcoinCashSignatureHashTypeEnabled,
                _parentUpgradeSchedule.isBitcoinCashSignatureHashTypeEnabled(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean areOnlyPushOperationsAllowedWithinUnlockingScript(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areOnlyPushOperationsAllowedWithinUnlockingScript,
                _parentUpgradeSchedule.areOnlyPushOperationsAllowedWithinUnlockingScript(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean isPayToScriptHashEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isPayToScriptHashEnabled,
                _parentUpgradeSchedule.isPayToScriptHashEnabled(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean isAsertDifficultyAdjustmentAlgorithmEnabled(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _isAsertDifficultyAdjustmentAlgorithmEnabled,
                _parentUpgradeSchedule.isAsertDifficultyAdjustmentAlgorithmEnabled(Util.coalesce(medianBlockTime, MedianBlockTime.MAX_VALUE))
        );
    }

    @Override
    public Boolean isCw144DifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isCw144DifficultyAdjustmentAlgorithmEnabled,
                _parentUpgradeSchedule.isCw144DifficultyAdjustmentAlgorithmEnabled(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean isEmergencyDifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isEmergencyDifficultyAdjustmentAlgorithmEnabled,
                _parentUpgradeSchedule.isEmergencyDifficultyAdjustmentAlgorithmEnabled(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean isBlockHeightWithinCoinbaseRequired(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isBlockHeightWithinCoinbaseRequired,
                _parentUpgradeSchedule.isBlockHeightWithinCoinbaseRequired(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean isRelativeLockTimeEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isRelativeLockTimeEnabled,
                _parentUpgradeSchedule.isRelativeLockTimeEnabled(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForTransactionLockTime(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _shouldUseMedianBlockTimeForTransactionLockTime,
                _parentUpgradeSchedule.shouldUseMedianBlockTimeForTransactionLockTime(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForBlockTimestamp(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _shouldUseMedianBlockTimeForBlockTimestamp,
                _parentUpgradeSchedule.shouldUseMedianBlockTimeForBlockTimestamp(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean isCheckLockTimeOperationEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isCheckLockTimeOperationEnabled,
                _parentUpgradeSchedule.isCheckLockTimeOperationEnabled(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean isCheckSequenceNumberOperationEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isCheckSequenceNumberOperationEnabled,
                _parentUpgradeSchedule.isCheckSequenceNumberOperationEnabled(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean areAllInvalidSignaturesRequiredToBeEmpty(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areAllInvalidSignaturesRequiredToBeEmpty,
                _parentUpgradeSchedule.areAllInvalidSignaturesRequiredToBeEmpty(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean areCanonicalSignatureEncodingsRequired(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areCanonicalSignatureEncodingsRequired,
                _parentUpgradeSchedule.areCanonicalSignatureEncodingsRequired(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean areSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areSignaturesRequiredToBeStrictlyEncoded,
                _parentUpgradeSchedule.areSignaturesRequiredToBeStrictlyEncoded(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean arePublicKeysRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _arePublicKeysRequiredToBeStrictlyEncoded,
                _parentUpgradeSchedule.arePublicKeysRequiredToBeStrictlyEncoded(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean areDerSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areDerSignaturesRequiredToBeStrictlyEncoded,
                _parentUpgradeSchedule.areDerSignaturesRequiredToBeStrictlyEncoded(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean areUnusedValuesAfterScriptExecutionDisallowed(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areUnusedValuesAfterScriptExecutionDisallowed,
                _parentUpgradeSchedule.areUnusedValuesAfterScriptExecutionDisallowed(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean areTransactionsLessThanOneHundredBytesDisallowed(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _areTransactionsLessThanOneHundredBytesDisallowed,
                _parentUpgradeSchedule.areTransactionsLessThanOneHundredBytesDisallowed(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean areUnusedValuesAfterSegwitScriptExecutionAllowed(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _areUnusedValuesAfterSegwitScriptExecutionAllowed,
                _parentUpgradeSchedule.areUnusedValuesAfterSegwitScriptExecutionAllowed(Util.coalesce(medianBlockTime, MedianBlockTime.MAX_VALUE))
        );
    }

    @Override
    public Boolean isSignatureOperationCountingVersionTwoEnabled(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _isSignatureOperationCountingVersionTwoEnabled,
                _parentUpgradeSchedule.isSignatureOperationCountingVersionTwoEnabled(Util.coalesce(medianBlockTime, MedianBlockTime.MAX_VALUE))
        );
    }

    @Override
    public Boolean isCheckDataSignatureOperationEnabled(final Long blockHeight) {
        return _isOverriddenOrEnabled(
                _isCheckDataSignatureOperationEnabled,
                _parentUpgradeSchedule.isCheckDataSignatureOperationEnabled(Util.coalesce(blockHeight, Long.MAX_VALUE))
        );
    }

    @Override
    public Boolean areSchnorrSignaturesEnabledWithinMultiSignature(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _areSchnorrSignaturesEnabledWithinMultiSignature,
                _parentUpgradeSchedule.areSchnorrSignaturesEnabledWithinMultiSignature(Util.coalesce(medianBlockTime, MedianBlockTime.MAX_VALUE))
        );
    }

    @Override
    public Boolean isReverseBytesOperationEnabled(final MedianBlockTime medianBlockTime) {
        return _isOverriddenOrEnabled(
                _isReverseBytesOperationEnabled,
                _parentUpgradeSchedule.isReverseBytesOperationEnabled(Util.coalesce(medianBlockTime, MedianBlockTime.MAX_VALUE))
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

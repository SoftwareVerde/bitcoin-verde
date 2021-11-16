package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public class CoreUpgradeSchedule implements UpgradeSchedule {
    private static final long BIP16_ACTIVATION_BLOCK_HEIGHT = 173805L; // Pay to Script Hash - https://github.com/bitcoin/bips/blob/master/bip-0016.mediawiki
    private static final long BIP34_ACTIVATION_BLOCK_HEIGHT = 227836L; // Block v2. Height in Coinbase - https://github.com/bitcoin/bips/blob/master/bip-0034.mediawiki
    private static final long BIP65_ACTIVATION_BLOCK_HEIGHT = 388167L; // OP_CHECKLOCKTIMEVERIFY -- https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki
    private static final long BIP66_ACTIVATION_BLOCK_HEIGHT = 363725L; // Strict DER signatures -- https://github.com/bitcoin/bips/blob/master/bip-0066.mediawiki
    private static final long BIP68_ACTIVATION_BLOCK_HEIGHT = 419328L; // Relative Lock-Time Using Consensus-Enforced Sequence Numbers - https://github.com/bitcoin/bips/blob/master/bip-0068.mediawiki
    private static final long BIP112_ACTIVATION_BLOCK_HEIGHT = 419328L; // OP_CHECKSEQUENCEVERIFY -- https://github.com/bitcoin/bips/blob/master/bip-0112.mediawiki
    private static final long BIP113_ACTIVATION_BLOCK_HEIGHT = 419328L; // Median Time-Past As Endpoint For LockTime Calculations -- https://github.com/bitcoin/bips/blob/master/bip-0113.mediawiki
    // NOTE: BCH-specific activation heights are usually one ahead of the BCHN activation heights
    //       since BCHN uses the previous block height for activations.
    private static final long BUIP55_ACTIVATION_BLOCK_HEIGHT = 478559L; // Bitcoin Cash: UAHF - https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/uahf-technical-spec.md
    private static final long HF20171113_ACTIVATION_BLOCK_HEIGHT = 504032L; // Bitcoin Cash: 2017-11-07 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/nov-13-hardfork-spec.md
    private static final long HF20180515_ACTIVATION_BLOCK_HEIGHT = 530359L; // Bitcoin Cash: 2018-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/may-2018-hardfork.md
    private static final long HF20181115_ACTIVATION_BLOCK_HEIGHT = 556767L; // Bitcoin Cash: 2018-11-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2018-nov-upgrade.md

    private static final long HF20190515_ACTIVATION_TIME = 1557921600L; // Bitcoin Cash: 2019-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-05-15-upgrade.md
    private static final long HF20191115_ACTIVATION_TIME = 1573819200L; // Bitcoin Cash: 2019-11-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-11-15-upgrade.md
    private static final long HF20200515_ACTIVATION_TIME = 1589544000L; // Bitcoin Cash: 2020-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2020-05-15-upgrade.md
    private static final long HF20201115_ACTIVATION_TIME = 1605441600L; // Bitcoin Cash: 2020-11-15 Hard Fork:  https://gitlab.com/bitcoin-cash-node/bchn-sw/bitcoincash-upgrade-specifications/-/blob/master/spec/2020-11-15-upgrade.md

    @Override
    public Boolean isMinimalNumberEncodingRequired(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= HF20191115_ACTIVATION_TIME);
    }

    @Override
    public Boolean isBitcoinCashSignatureHashTypeEnabled(final Long blockHeight) {
        return (blockHeight >= BUIP55_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areOnlyPushOperationsAllowedWithinUnlockingScript(final Long blockHeight) {
        return (blockHeight >= HF20181115_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isPayToScriptHashEnabled(final Long blockHeight) {
        return (blockHeight >= BIP16_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isAsertDifficultyAdjustmentAlgorithmEnabled(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= HF20201115_ACTIVATION_TIME);
    }

    @Override
    public Boolean isCw144DifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return (blockHeight >= HF20171113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isEmergencyDifficultyAdjustmentAlgorithmEnabled(final Long blockHeight) {
        return (blockHeight >= BUIP55_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isBlockHeightWithinCoinbaseRequired(final Long blockHeight) {
        return (blockHeight >= BIP34_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isRelativeLockTimeEnabled(final Long blockHeight) {
        return (blockHeight >= BIP68_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForTransactionLockTime(final Long blockHeight) {
        return (blockHeight >= BIP113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean shouldUseMedianBlockTimeForBlockTimestamp(final Long blockHeight) {
        return (blockHeight >= BIP113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean isCheckLockTimeOperationEnabled(final Long blockHeight) {
        return blockHeight >= BIP65_ACTIVATION_BLOCK_HEIGHT;
    }

    @Override
    public Boolean isCheckSequenceNumberOperationEnabled(final Long blockHeight) {
        return (blockHeight >= BIP112_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areAllInvalidSignaturesRequiredToBeEmpty(final Long blockHeight) {
        return (blockHeight >= HF20171113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return (blockHeight >= BUIP55_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areCanonicalSignatureEncodingsRequired(final Long blockHeight) {
        return (blockHeight >= HF20171113_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean arePublicKeysRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return (blockHeight >= BUIP55_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areDerSignaturesRequiredToBeStrictlyEncoded(final Long blockHeight) {
        return (blockHeight >= BIP66_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areUnusedValuesAfterScriptExecutionDisallowed(final Long blockHeight) {
        return (blockHeight >= HF20181115_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areTransactionsLessThanOneHundredBytesDisallowed(final Long blockHeight) {
        return (blockHeight >= HF20181115_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areUnusedValuesAfterSegwitScriptExecutionAllowed(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= HF20190515_ACTIVATION_TIME);
    }


    @Override
    public Boolean isSignatureOperationCountingVersionTwoEnabled(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= HF20200515_ACTIVATION_TIME);
    }

    @Override
    public Boolean isCheckDataSignatureOperationEnabled(final Long blockHeight) {
        return (blockHeight >= HF20181115_ACTIVATION_BLOCK_HEIGHT);
    }

    @Override
    public Boolean areSchnorrSignaturesEnabledWithinMultiSignature(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= HF20191115_ACTIVATION_TIME);
    }

    @Override
    public Boolean isReverseBytesOperationEnabled(final MedianBlockTime medianBlockTime) {
        return (medianBlockTime.getCurrentTimeInSeconds() >= HF20200515_ACTIVATION_TIME);
    }
}

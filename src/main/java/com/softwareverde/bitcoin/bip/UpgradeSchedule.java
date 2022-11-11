package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public interface UpgradeSchedule {
    enum UpgradeHeight {
        BIP16_ACTIVATION_BLOCK_HEIGHT(0),       // Pay to Script Hash - https://github.com/bitcoin/bips/blob/master/bip-0016.mediawiki
        BIP34_ACTIVATION_BLOCK_HEIGHT(1),       // Block v2. Height in Coinbase - https://github.com/bitcoin/bips/blob/master/bip-0034.mediawiki
        BIP65_ACTIVATION_BLOCK_HEIGHT(2),       // OP_CHECKLOCKTIMEVERIFY -- https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki
        BIP66_ACTIVATION_BLOCK_HEIGHT(3),       // Strict DER signatures -- https://github.com/bitcoin/bips/blob/master/bip-0066.mediawiki
        BIP68_ACTIVATION_BLOCK_HEIGHT(4),       // Relative Lock-Time Using Consensus-Enforced Sequence Numbers - https://github.com/bitcoin/bips/blob/master/bip-0068.mediawiki
        BIP112_ACTIVATION_BLOCK_HEIGHT(5),      // OP_CHECKSEQUENCEVERIFY -- https://github.com/bitcoin/bips/blob/master/bip-0112.mediawiki
        BIP113_ACTIVATION_BLOCK_HEIGHT(6),      // Median Time-Past As Endpoint For LockTime Calculations -- https://github.com/bitcoin/bips/blob/master/bip-0113.mediawiki

        // NOTE: BCH-specific activation heights are usually one ahead of the BCHN activation heights since BCHN uses the previous block height for activations.
        BUIP55_ACTIVATION_BLOCK_HEIGHT(7),      // Bitcoin Cash: UAHF - https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/uahf-technical-spec.md
        HF20171113_ACTIVATION_BLOCK_HEIGHT(8),  // Bitcoin Cash: 2017-11-07 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/nov-13-hardfork-spec.md
        HF20180515_ACTIVATION_BLOCK_HEIGHT(9),  // Bitcoin Cash: 2018-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/may-2018-hardfork.md
        HF20181115_ACTIVATION_BLOCK_HEIGHT(10); // Bitcoin Cash: 2018-11-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2018-nov-upgrade.md

        public final int value;
        UpgradeHeight(final int value) {
            this.value = value;
        }
    }

    enum UpgradeTime {
        HF20190515_ACTIVATION_TIME(0),         // Bitcoin Cash: 2019-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-05-15-upgrade.md
        HF20191115_ACTIVATION_TIME(1),         // Bitcoin Cash: 2019-11-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-11-15-upgrade.md
        HF20200515_ACTIVATION_TIME(2),         // Bitcoin Cash: 2020-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2020-05-15-upgrade.md
        HF20201115_ACTIVATION_TIME(3),         // Bitcoin Cash: 2020-11-15 Hard Fork:  https://gitlab.com/bitcoin-cash-node/bchn-sw/bitcoincash-upgrade-specifications/-/blob/master/spec/2020-11-15-upgrade.md
        HF20220515_ACTIVATION_TIME(4),         // Bitcoin Cash: 2022-05-15 Hard Fork:  https://gitlab.com/bitcoin-cash-node/bchn-sw/bitcoincash-upgrade-specifications/-/blob/master/spec/2022-05-15-upgrade.md
        HF20230515_ACTIVATION_TIME(5);         // Bitcoin Cash: 2023-05-15 Hard Fork: https://bitcoincashresearch.org/t/2021-bch-upgrade-items-brainstorm/130/29 // TODO: Update upgrade spec.

        public final int value;
        UpgradeTime(final int value) {
            this.value = value;
        }
    }

    Boolean didUpgradeActivate(Long blockHeight0, MedianBlockTime medianBlockTime0, Long blockHeight1, MedianBlockTime medianBlockTime1);

    Boolean isMinimalNumberEncodingRequired(MedianBlockTime medianBlockTime); // HF20191115
    Boolean isBitcoinCashSignatureHashTypeEnabled(Long blockHeight); // Buip 55
    Boolean areOnlyPushOperationsAllowedWithinUnlockingScript(Long blockHeight); // HF20181115 / Bip 62
    Boolean isLegacyPayToScriptHashEnabled(Long blockHeight); // Bip 16
    Boolean isSha256PayToScriptHashEnabled(MedianBlockTime medianBlockTime); // HF20230515
    Boolean isAsertDifficultyAdjustmentAlgorithmEnabled(MedianBlockTime medianBlockTime); // HF20201115
    Boolean isCw144DifficultyAdjustmentAlgorithmEnabled(Long blockHeight); // HF20171113
    Boolean isEmergencyDifficultyAdjustmentAlgorithmEnabled(Long blockHeight); // Buip 55
    Boolean isBlockHeightWithinCoinbaseRequired(Long blockHeight); // Bip 34

    /**
     * Creates a new consensus-enforced semantics of the SequenceNumber field to enable a signed transaction input to
     *  remain invalid for a defined period of time after confirmation of its previous TransactionOutput.
     * Enabled in Bip 68.
     */
    Boolean isRelativeLockTimeEnabled(Long blockHeight); // Bip 68

    /**
     * Redefines the semantics used in determining a time-locked Transaction's eligibility for inclusion in a Block.
     * The median of the last 11 Blocks is used instead of the Block's timestamp, ensuring that it increases
     *  monotonically with each block.
     * Enabled in Bip 113.
     */
    Boolean shouldUseMedianBlockTimeForTransactionLockTime(Long blockHeight); // Bip 113

    /**
     * Enabled in Bip 113.
     */
    Boolean shouldUseMedianBlockTimeForBlockTimestamp(Long blockHeight); // Bip 113

    /**
     * Enabled in Bip 65.
     */
    Boolean isCheckLockTimeOperationEnabled(Long blockHeight); // Bip 65

    /**
     * Enabled in Bip 112.
     */
    Boolean isCheckSequenceNumberOperationEnabled(Long blockHeight); // Bip 112

    /**
     * All executed Signature checks must be valid unless the Signature is empty (i.e. has zero bytes).
     * Also known as "NULLFAIL".
     * Enabled in HF20171113 / Bip 146.
     */
    Boolean areAllInvalidSignaturesRequiredToBeEmpty(Long blockHeight); // Bip 146

    /**
     * Require that the S value within ECDSA signatures is at most the curve order divided by 2.
     *  This essentially restricts the S value to its lower half range.
     * This feature reduces Transaction malleability.
     * Also known as "LOW_S".
     * Enabled in HF20171113 / Bip 146.
     */
    Boolean areCanonicalSignatureEncodingsRequired(Long blockHeight); // HF20171113 / Bip 146

    /**
     * Transaction Signatures have various loosely defined formats.
     * Some of these defined formats include unused bytes after the encoded R/S signature values,
     *  undefined/nonstandard HashTypes, non-BCH HashTypes, and negative non-canonical DER formats.
     *  These various forms increase Transaction malleability.
     * Also known as "Signature Strict Encoding" rule, and "SCRIPT_VERIFY_STRICTENC".
     * Enabled in Buip 55.
     */
    Boolean areSignaturesRequiredToBeStrictlyEncoded(Long blockHeight); // Buip 55

    /**
     * Transaction Signatures have various loosely defined formats.
     * Require that all Values used as a PublicKey within a Signature check are formatted correctly, otherwise
     *  fail the entire Script.
     * This feature only requires a valid encoding and does not require the PublicKey be canonically formatted.
     * Enabled in Buip 55.
     */
    Boolean arePublicKeysRequiredToBeStrictlyEncoded(Long blockHeight); // Buip 55

    /**
     * Enforce a strict encoding for DER-encoded signatures.
     * Negative R and S encodings for DER encoded signatures are historically allowed but introduced malleability.
     * This feature reduces malleability by enforcing DER encodings use their non-negative canonical encoding.
     * Enabled in Bip 66.
     */
    Boolean areDerSignaturesRequiredToBeStrictlyEncoded(Long blockHeight);

    /**
     * Dirty stacks are considered invalid after HF20181115 in order to reduce malleability.
     * Also known as the "Clean Stack" rule, and "CLEANSTACK".
     * Enabled in HF20181115.
     */
    Boolean areUnusedValuesAfterScriptExecutionDisallowed(Long blockHeight);

    /**
     * Requires Transactions be at least 100 bytes in size.
     * This feature protects SPV Wallet users from a Merkle Tree vulnerability that allows attackers to spoof transactions.
     * Enabled in HF20181115.
     * Deprecated as of HF20230515 when the minimum was reduced to 65 bytes.
     */
    @Deprecated
    Boolean areTransactionsLessThanOneHundredBytesDisallowed(Long blockHeight); // TODO: Remove after HF20230815.

    /**
     * Requires Transactions be at least 65 bytes in size.
     * This feature protects SPV Wallet users from a Merkle Tree vulnerability that allows attackers to spoof transactions.
     * Enabled in HF20230515; the minimum was reduced from 100 to 65 in HF20230515.
     */
    Boolean areTransactionsLessThanSixtyFiveBytesDisallowed(MedianBlockTime medianBlockTime);

    /**
     * Enable an exemption from the Dirty Stack rule for Segwit P2SH Scripts.
     * Scripts accidentally using Segwit P2SH Scripts were made un-spendable by the Clean Stack rule.
     * Enabled in HF20190515.
     */
    Boolean areUnusedValuesAfterSegwitScriptExecutionAllowed(MedianBlockTime medianBlockTime);

    /**
     * Signature check operations ("SigChecks") are counted in order to prevent DOS attacks that make validating a Block
     *  intentionally slow for users and miners that did not produce the Block.
     * This limit was also known as the "SigOps Limit".
     * Version 1 of SigOps limit used complicated heuristics that inaccurately measured the difficulty of validating
     *  the block. SigChecks (SigOps Limit, Version 2) simplifies the counting method and more accurately represents
     *  the CPU-cost to validate the block.
     * Enabled in HF20200515.
     */
    Boolean isSignatureOperationCountingVersionTwoEnabled(MedianBlockTime medianBlockTime);

    /**
     * The CheckDataSignature Operation checks whether a signature is valid with respect to a message and a PublicKey.
     * Enabled in HF20181115.
     */
    Boolean isCheckDataSignatureOperationEnabled(Long blockHeight);

    /**
     * Enable use of Schnorr Signatures within CheckMultiSig Operations.
     * The "dummy" element within the old format is repurposed for Schnorr when it is non-null.
     * Enabled in HF20191115.
     */
    Boolean areSchnorrSignaturesEnabledWithinMultiSignature(MedianBlockTime medianBlockTime);

    /**
     * The ReverseBytes Operation reverses the order of bytes in a Value. It can be used to change endianness.
     * Enabled in HF20200515.
     */
    Boolean isReverseBytesOperationEnabled(MedianBlockTime medianBlockTime);

    /**
     * Introspection Operations push information about the Transaction to the script's stack.
     * Enabled in HF20220515.
     */
    Boolean areIntrospectionOperationsEnabled(MedianBlockTime medianBlockTime);

    /**
     * 64-bit script integers are used for most script operations, with overflow validation.
     * Enabled in HF20220515.
     */
    Boolean are64BitScriptIntegersEnabled(MedianBlockTime medianBlockTime);

    /**
     * Enables the MULTIPLY Opcode.
     * Enabled in HF20220515.
     */
    Boolean isMultiplyOperationEnabled(MedianBlockTime medianBlockTime);

    /**
     * Requires all Transactions use versions 1 or 2.
     * Enabled in HF20230515.
     */
    Boolean areTransactionVersionsRestricted(MedianBlockTime medianBlockTime);

    /**
     * Enabled in HF20230515.
     */
    Boolean areCashTokensEnabled(MedianBlockTime medianBlockTime);
}

package com.softwareverde.bitcoin.bip;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public interface UpgradeSchedule {
    // TODO: Functions returning booleans should be named as questions.

    Boolean isMinimalNumberEncodingRequired(MedianBlockTime medianBlockTime); // HF20191115
    Boolean isBitcoinCashSignatureHashTypeEnabled(Long blockHeight); // Buip 55
    Boolean disallowNonPushOperationsWithinUnlockingScript(Long blockHeight); // HF20181115 / Bip 62
    Boolean isPayToScriptHashEnabled(Long blockHeight); // Bip 16

    Boolean isAsertDifficultyAdjustmentAlgorithmEnabled(MedianBlockTime medianBlockTime); // HF20201115

    Boolean isCw144DifficultyAdjustmentAlgorithmEnabled(Long blockHeight); // HF20171113

    Boolean isEmergencyDifficultyAdjustmentAlgorithmEnabled(Long blockHeight); // Buip 55

    Boolean requireBlockHeightWithinCoinbase(Long blockHeight); // Bip 34

    /**
     * Creates a new consensus-enforced semantics of the SequenceNumber field to enable a signed transaction input to
     *  remain invalid for a defined period of time after confirmation of its previous TransactionOutput.
     * Enabled in Bip 68.
     */
    Boolean isRelativeLockTimeEnabled(Long blockHeight);

    /**
     * Redefines the semantics used in determining a time-locked Transaction's eligibility for inclusion in a Block.
     * The median of the last 11 Blocks is used instead of the Block's timestamp, ensuring that it increases
     *  monotonically with each block.
     * Enabled in Bip 113.
     */
    Boolean enableMedianBlockTimeForTransactionLockTime(Long blockHeight);

    /**
     * Enabled in Bip 113.
     */
    Boolean enableMedianBlockTimeForBlockTimestamp(Long blockHeight);

    /**
     * Enabled in Bip 65.
     */
    Boolean isCheckLockTimeOperationEnabled(Long blockHeight);

    /**
     * Enabled in Bip 112.
     */
    Boolean isCheckSequenceNumberOperationEnabled(Long blockHeight);

    /**
     * All executed Signature checks must be valid unless the Signature is empty (i.e. has zero bytes).
     * Also known as "NULLFAIL".
     * Enabled in HF20171113 / Bip 146.
     */
    Boolean requireAllInvalidSignaturesBeEmpty(Long blockHeight);

    /**
     * Transaction Signatures and PublicKeys have various loosely defined formats.
     * Some of these defined formats include unused bytes after the encoded R/S signature values,
     *  undefined/nonstandard HashTypes, non-BCH HashTypes, and negative non-canonical DER formats.
     *  These various forms increase Transaction malleability.
     * Also known as "Signature Strict Encoding" rule, and "SCRIPT_VERIFY_STRICTENC".
     * Enabled in Buip 55.
     */
    Boolean requireStrictSignatureAndPublicKeyEncoding(Long blockHeight);

    /**
     * Require that the S value within ECDSA signatures is at most the curve order divided by 2.
     *  This essentially restricts the S value to its lower half range.
     * This feature reduces Transaction malleability.
     * Also known as "LOW_S".
     * Enabled in HF20171113 / Bip 146.
     */
    Boolean requireCanonicalSignatureEncoding(Long blockHeight);

    /**
     * Require that all Values used as a PublicKey within a Signature check are formatted correctly, otherwise
     *  fail the entire Script.
     * This feature only requires a valid encoding and does not require the PublicKey be canonically formatted.
     * Enabled in Buip 55.
     */
    Boolean requireStrictPublicKeyEncoding(Long blockHeight);

    /**
     * Enforce a strict encoding for DER-encoded signatures.
     * Negative R and S encodings for DER encoded signatures are historically allowed but introduced malleability.
     * This feature reduces malleability by enforcing DER encodings use their non-negative canonical encoding.
     * Enabled in Bip 66.
     */
    Boolean disallowNegativeDerSignatureEncodings(Long blockHeight);

    /**
     * Dirty stacks are considered invalid after HF20181115 in order to reduce malleability.
     * Also known as the "Clean Stack" rule, and "CLEANSTACK".
     * Enabled in HF20181115.
     */
    Boolean disallowUnusedValuesAfterScriptExecution(Long blockHeight);

    /**
     * Requires Transactions be at least 100 bytes in size.
     * This feature protects SPV Wallet users from a Merkle Tree vulnerability that allows attackers to spoof transactions.
     * Enabled in HF20181115.
     */
    Boolean enableMinimumTransactionByteCount(Long blockHeight);

    /**
     * Enable an exemption from the Dirty Stack rule for Segwit P2SH Scripts.
     * Scripts accidentally using Segwit P2SH Scripts were made un-spendable by the Clean Stack rule.
     * Enabled in HF20190515.
     */
    Boolean allowUnusedValuesAfterScriptExecutionForSegwitScripts(MedianBlockTime medianBlockTime);

    /**
     * Signature check operations ("SigChecks") are counted in order to prevent DOS attacks that make validating a Block
     *  intentionally slow for users and miners that did not produce the Block.
     * This limit was also known as the "SigOps Limit".
     * Version 1 of SigOps limit used complicated heuristics that inaccurately measured the difficulty of validating
     *  the block. SigChecks (SigOps Limit, Version 2) simplifies the counting method and more accurately represents
     *  the CPU-cost to validate the block.
     * Enabled in HF20200515.
     */
    Boolean enableSignatureOperationCountingVersion2(MedianBlockTime medianBlockTime);

    /**
     * MultiSignature Check Scripts historically allowed the Values encoding the number of PublicKeys and Signatures be
     *  ambiguously coded. This feature requires the encoding is canonical.
     * This features reduces Transaction malleability.
     * Enabled in HF20191115.
     */
    Boolean requireMultiSignatureChecksAreCanonicallyEncoded(MedianBlockTime medianBlockTime);

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
}

package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class BitcoinCashTransactionSignerUtil {
    protected static final int FORK_ID = 0x000000;

    public static Boolean shouldSequenceNumbersDigestBeEmptyHash(final HashType hashType) {
        if (! hashType.shouldSignOtherInputs()) { return true; }

        final Mode hashTypeMode = hashType.getMode();
        if (hashTypeMode == Mode.SIGNATURE_HASH_NONE) { return true; }
        if (hashTypeMode == Mode.SIGNATURE_HASH_SINGLE) { return true; }

        return false;
    }

    public static ByteArray getTransactionVersionBytes(final Transaction transaction) {
        final Long transactionVersion = transaction.getVersion();
        return BitcoinCashTransactionSignerUtil.getTransactionVersionBytes(transactionVersion);
    }
    /**
     * Get the Bitcoin Cash Transaction signing preimage property (#1) that is the serialization of the Transaction's version.
     */
    public static ByteArray getTransactionVersionBytes(final Long transactionVersion) {
        return ByteArray.wrap(ByteUtil.integerToBytes(transactionVersion)).toReverseEndian();
    }

    /**
     * Get the Bitcoin Cash Transaction signing preimage property (#2) that is the serialization of the Transaction's
     *  TransactionInputs' previous TransactionOutputIdentifiers.
     */
    public static Sha256Hash getPreviousOutputIdentifiersHash(final Transaction transaction, final HashType hashType) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        if (hashType.shouldSignOtherInputs()) {
            final ByteArrayBuilder serializedTransactionInput = new ByteArrayBuilder();
            for (final TransactionInput transactionInput : transactionInputs) {
                serializedTransactionInput.appendBytes(transactionInput.getPreviousOutputTransactionHash(), Endian.LITTLE);
                serializedTransactionInput.appendBytes(ByteUtil.integerToBytes(transactionInput.getPreviousOutputIndex()), Endian.LITTLE);
            }

            return HashUtil.doubleSha256(serializedTransactionInput);
        }

        return Sha256Hash.EMPTY_HASH;
    }

    /**
     * Get the Bitcoin Cash Transaction signing preimage property (#3) that is the serialization of the Transaction's
     * TransactionInputs' SequenceNumbers.
     */
    public static Sha256Hash getTransactionInputsSequenceNumbersHash(final Transaction transaction, final HashType hashType) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final Boolean sequenceNumbersDigestShouldBeEmptyHash = BitcoinCashTransactionSignerUtil.shouldSequenceNumbersDigestBeEmptyHash(hashType);
        if (sequenceNumbersDigestShouldBeEmptyHash) {
            return Sha256Hash.EMPTY_HASH;
        }

        final ByteArrayBuilder serializedSequenceNumbers = new ByteArrayBuilder();
        for (final TransactionInput transactionInput : transactionInputs) {
            final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
            serializedSequenceNumbers.appendBytes(sequenceNumber.getBytes(), Endian.LITTLE);
        }

        return HashUtil.doubleSha256(serializedSequenceNumbers);
    }

    /**
     * Get the Bitcoin Cash Transaction signing preimage property (#4) that is the serialization the signed
     *  TransactionInput's previous TransactionOutputIdentifier.
     */
    public static ByteArray getPreviousTransactionOutputIdentifierBytes(final Transaction transaction, final Integer inputIndex) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final TransactionInput transactionInput = transactionInputs.get(inputIndex);

        final ByteArrayBuilder serializedTransactionOutputBeingSpent = new ByteArrayBuilder();
        serializedTransactionOutputBeingSpent.appendBytes(transactionInput.getPreviousOutputTransactionHash(), Endian.LITTLE);
        serializedTransactionOutputBeingSpent.appendBytes(ByteUtil.integerToBytes(transactionInput.getPreviousOutputIndex()), Endian.LITTLE);
        return serializedTransactionOutputBeingSpent;
    }

    /**
     * Get the Bitcoin Cash Transaction signing preimage property (#6) that is the serialization of the amount of the
     *  previous/spent TransactionOutput.
     */
    public static ByteArray getPreviousTransactionOutputAmountBytes(final TransactionOutput transactionOutput) {
        final Long amount = transactionOutput.getAmount();
        return ByteArray.wrap(ByteUtil.longToBytes(amount)).toReverseEndian();
    }

    /**
     * Get the Bitcoin Cash Transaction signing preimage property (#7) that is the serialization of the SequenceNumber
     *  for the provided TransactionInput.
     */
    public static ByteArray getSequenceNumberBytes(final Transaction transaction, final Integer inputIndex) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final TransactionInput transactionInput = transactionInputs.get(inputIndex);

        final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
        return sequenceNumber.getBytes().toReverseEndian();
    }

    /**
     * Get the Bitcoin Cash Transaction signing preimage property (#8) that is the serialization of the Transaction's
     *  TransactionOutputs.
     */
    public static Sha256Hash getTransactionOutputsHash(final Transaction transaction, final Integer inputIndex, final HashType hashType) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        if (hashType.getMode() == Mode.SIGNATURE_HASH_NONE) {
            return Sha256Hash.EMPTY_HASH;
        }

        if (hashType.getMode() == Mode.SIGNATURE_HASH_SINGLE) {
            if (inputIndex >= transactionOutputs.getCount()) {
                return Sha256Hash.EMPTY_HASH; // NOTE: This is different behavior than Bitcoin (BTC) for this error case...
            }

            final TransactionOutput transactionOutput = transactionOutputs.get(inputIndex);
            final LockingScript transactionOutputScript = transactionOutput.getLockingScript();

            final ByteArrayBuilder serializedTransactionOutput = new ByteArrayBuilder();

            serializedTransactionOutput.appendBytes(ByteUtil.longToBytes(transactionOutput.getAmount()), Endian.LITTLE);
            serializedTransactionOutput.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionOutputScript.getByteCount()));
            serializedTransactionOutput.appendBytes(transactionOutputScript.getBytes());

            return HashUtil.doubleSha256(serializedTransactionOutput);
        }

        final ByteArrayBuilder serializedTransactionOutput = new ByteArrayBuilder();
        for (final TransactionOutput transactionOutput : transactionOutputs) {
            final LockingScript transactionOutputScript = transactionOutput.getLockingScript();

            serializedTransactionOutput.appendBytes(ByteUtil.longToBytes(transactionOutput.getAmount()), Endian.LITTLE);
            serializedTransactionOutput.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionOutputScript.getByteCount()));
            serializedTransactionOutput.appendBytes(transactionOutputScript.getBytes());
        }
        return HashUtil.doubleSha256(serializedTransactionOutput);
    }

    public static ByteArray getTransactionLockTimeBytes(final Transaction transaction) {
        final LockTime lockTime = transaction.getLockTime();
        return BitcoinCashTransactionSignerUtil.getTransactionLockTimeBytes(lockTime).toReverseEndian();
    }

    /**
     * Get the Bitcoin Cash Transaction signing preimage property (#9) that is the serialization of the Transaction's LockTime.
     */
    public static ByteArray getTransactionLockTimeBytes(final LockTime lockTime) {
        return lockTime.getBytes();
    }

    /**
     * Get the Bitcoin Cash Transaction signing preimage property (#10) that is the serialization of the Transaction's HashType.
     */
    public static ByteArray getTransactionHashTypeBytes(final HashType hashType) {
        final byte hashTypeByte = hashType.toByte();
        final byte[] hashTypeWithForkId = ByteUtil.integerToBytes(FORK_ID << 8);
        hashTypeWithForkId[3] |= hashTypeByte;
        return ByteArray.wrap(hashTypeWithForkId).toReverseEndian();
    }

    protected BitcoinCashTransactionSignerUtil() { }
}

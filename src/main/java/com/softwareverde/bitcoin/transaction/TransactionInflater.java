package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputInflater;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.Endian;

public class TransactionInflater {
    protected MutableTransaction _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        // NOTE: The min Transaction size rule was activated on HF20181115 and therefore cannot be enforced here.

        final Integer startPosition = byteArrayReader.getPosition();
        final MutableTransaction transaction = new MutableTransaction();
        transaction._version = byteArrayReader.readLong(4, Endian.LITTLE);

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final Long transactionInputCount = byteArrayReader.readVariableLengthInteger();
        for (int i = 0; i < transactionInputCount; ++i) {
            if (byteArrayReader.remainingByteCount() < 1) { return null; }
            final MutableTransactionInput transactionInput = transactionInputInflater.fromBytes(byteArrayReader);
            if (transactionInput == null) { return null; }
            transaction._transactionInputs.add(transactionInput);
        }

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final Long transactionOutputCount = byteArrayReader.readVariableLengthInteger();
        for (int i = 0; i < transactionOutputCount; ++i) {
            if (byteArrayReader.remainingByteCount() < 1) { return null; }
            final MutableTransactionOutput transactionOutput = transactionOutputInflater.fromBytes(i, byteArrayReader);
            if (transactionOutput == null) { return null; }
            transaction._transactionOutputs.add(transactionOutput);
        }

        { // Read Transaction LockTime...
            final Long lockTimeValue = byteArrayReader.readLong(4, Endian.LITTLE);
            transaction._lockTime = new ImmutableLockTime(lockTimeValue);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        final int totalByteCount;
        { // Enforce maximum transaction size...
            // NOTE: At this point, the bytes are already in memory and limited by the PacketBuffer max size, so incremental checks are not performed.
            final Integer endPosition = byteArrayReader.getPosition();
            totalByteCount = (endPosition - startPosition);
            if (totalByteCount > BitcoinConstants.getTransactionMaxByteCount()) { return null; }
        }

        transaction.cacheByteCount(totalByteCount);

        return transaction;
    }

    public void debugBytes(final ByteArrayReader byteArrayReader) {
        System.out.println("Version: " + HexUtil.toHexString(byteArrayReader.readBytes(4)));

        {
            final ByteArrayReader.CompactVariableLengthInteger inputCount = byteArrayReader.peakVariableLengthInteger();
            System.out.println("Tx Input Count: " + HexUtil.toHexString(byteArrayReader.readBytes(inputCount.bytesConsumedCount)));

            final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
            for (int i = 0; i < inputCount.value; ++i) {
                transactionInputInflater._debugBytes(byteArrayReader);
            }
        }

        {
            final ByteArrayReader.CompactVariableLengthInteger outputCount = byteArrayReader.peakVariableLengthInteger();
            System.out.println("Tx Output Count: " + HexUtil.toHexString(byteArrayReader.readBytes(outputCount.bytesConsumedCount)));
            final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
            for (int i = 0; i < outputCount.value; ++i) {
                transactionOutputInflater._debugBytes(byteArrayReader);
            }
        }

        System.out.println("LockTime: " + HexUtil.toHexString(byteArrayReader.readBytes(4)));
    }

    public Transaction fromBytes(final ByteArrayReader byteArrayReader) {
        if (byteArrayReader == null) { return null; }

        return _fromByteArrayReader(byteArrayReader);
    }

    public Transaction fromBytes(final byte[] bytes) {
        if (bytes == null) { return null; }

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }

    public Transaction fromBytes(final ByteArray bytes) {
        if (bytes == null) { return null; }

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }

    public Transaction createCoinbaseTransaction(final Long blockHeight, final String coinbaseMessage, final Address address, final Long satoshis) {
        final MutableTransaction coinbaseTransaction = new MutableTransaction();
        coinbaseTransaction.addTransactionInput(TransactionInput.createCoinbaseTransactionInput(blockHeight, coinbaseMessage));
        coinbaseTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address, satoshis));
        return coinbaseTransaction;
    }

    public Transaction createCoinbaseTransactionWithExtraNonce(final Long blockHeight, final String coinbaseMessage, final Integer extraNonceByteCount, final Address address, final Long satoshis) {
        final MutableTransaction coinbaseTransaction = new MutableTransaction();
        coinbaseTransaction.addTransactionInput(TransactionInput.createCoinbaseTransactionInputWithExtraNonce(blockHeight, coinbaseMessage, extraNonceByteCount));
        coinbaseTransaction.addTransactionOutput(TransactionOutput.createPayToAddressTransactionOutput(address, satoshis));
        return coinbaseTransaction;
    }
}

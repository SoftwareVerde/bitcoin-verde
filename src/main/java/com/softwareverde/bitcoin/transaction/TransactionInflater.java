package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class TransactionInflater {
    protected Transaction _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final Transaction transaction = new Transaction();
        transaction._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final Integer transactionInputCount = byteArrayReader.readVariableSizedInteger().intValue();
        for (int i=0; i<transactionInputCount; ++i) {
            if (byteArrayReader.remainingByteCount() <= 1) { return null; }
            final TransactionInput transactionInput = transactionInputInflater.fromBytes(byteArrayReader);
            if (transactionInput == null) { return null; }
            transaction._transactionInputs.add(transactionInput);
        }

        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final Integer transactionOutputCount = byteArrayReader.readVariableSizedInteger().intValue();
        for (int i=0; i<transactionOutputCount; ++i) {
            if (byteArrayReader.remainingByteCount() <= 1) { return null; }
            final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(i, byteArrayReader);
            if (transactionOutput == null) { return null; }
            transaction._transactionOutputs.add(transactionOutput);
        }

        final Long lockTimeTimestamp = byteArrayReader.readLong(4, Endian.LITTLE);
        transaction._lockTime.setLockTime(lockTimeTimestamp);

        return transaction;
    }

    public Transaction fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public Transaction fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }
}

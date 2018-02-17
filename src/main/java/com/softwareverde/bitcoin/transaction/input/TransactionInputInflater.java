package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class TransactionInputInflater {
    protected TransactionInput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final TransactionInput transactionInput = new TransactionInput();

        transactionInput._previousTransactionOutputHash = byteArrayReader.readBytes(32, Endian.LITTLE);
        transactionInput._previousTransactionOutputIndex = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer scriptByteCount = byteArrayReader.readVariableSizedInteger().intValue();
        transactionInput._signatureScript = byteArrayReader.readBytes(scriptByteCount, Endian.LITTLE);
        transactionInput._sequenceNumber = byteArrayReader.readInteger(4, Endian.LITTLE);

        return transactionInput;
    }

    public TransactionInput fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public TransactionInput fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }
}

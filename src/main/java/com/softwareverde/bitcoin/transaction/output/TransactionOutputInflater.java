package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class TransactionOutputInflater {
    protected TransactionOutput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final TransactionOutput transactionOutput = new TransactionOutput();

        transactionOutput._value = byteArrayReader.readLong(8, Endian.LITTLE);

        final Integer scriptByteCount = byteArrayReader.readVariableSizedInteger().intValue();
        transactionOutput._script = byteArrayReader.readBytes(scriptByteCount, Endian.LITTLE);

        return transactionOutput;
    }

    public TransactionOutput fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public TransactionOutput fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }
}

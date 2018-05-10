package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class TransactionOutputInflater {
    protected TransactionOutput _fromByteArrayReader(final Integer index, final ByteArrayReader byteArrayReader) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();

        transactionOutput._amount = byteArrayReader.readLong(8, Endian.LITTLE);
        transactionOutput._index = index;

        final Integer scriptByteCount = byteArrayReader.readVariableSizedInteger().intValue();
        transactionOutput._lockingScript = new ImmutableLockingScript(byteArrayReader.readBytes(scriptByteCount, Endian.BIG));

        if (byteArrayReader.didOverflow()) { return null; }

        return transactionOutput;
    }

    public TransactionOutput fromBytes(final Integer index, final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(index, byteArrayReader);
    }

    public TransactionOutput fromBytes(final Integer index, final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(index, byteArrayReader);
    }
}

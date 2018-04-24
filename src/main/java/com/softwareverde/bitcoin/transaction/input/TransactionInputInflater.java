package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class TransactionInputInflater {
    protected MutableTransactionInput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableTransactionInput transactionInput = new MutableTransactionInput();

        transactionInput._previousOutputTransactionHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
        transactionInput._previousOutputIndex = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer scriptByteCount = byteArrayReader.readVariableSizedInteger().intValue();
        transactionInput._unlockingScript = new ImmutableUnlockingScript(byteArrayReader.readBytes(scriptByteCount, Endian.BIG));
        transactionInput._sequenceNumber = byteArrayReader.readLong(4, Endian.LITTLE);

        if (byteArrayReader.didOverflow()) { return null; }

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

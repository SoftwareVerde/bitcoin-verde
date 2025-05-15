package com.softwareverde.bitcoin.server.module.node;

import com.google.leveldb.LevelDb;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class IndexedTransactionEntryInflater implements LevelDb.EntryInflater<Long, IndexedTransaction> {
    @Override
    public Long keyFromBytes(final ByteArray byteArray) {
        return ByteUtil.bytesToLong(byteArray.getBytes());
    }

    @Override
    public ByteArray keyToBytes(final Long transactionId) {
        return MutableByteArray.wrap(ByteUtil.integerToBytes(transactionId)); // NOTE: Only stored via 4 bytes.
    }

    @Override
    public IndexedTransaction valueFromBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }
        if (byteArray.isEmpty()) { return null; }
        final Sha256Hash hash = Sha256Hash.wrap(byteArray.getBytes(0, Sha256Hash.BYTE_COUNT));
        final Long blockHeight = ByteUtil.bytesToLong(byteArray.getBytes(32, 4)); // NOTE: Only stored via 4 bytes.
        final int diskOffset = ByteUtil.bytesToInteger(byteArray.getBytes(36, 4));
        final int byteCount = ByteUtil.bytesToInteger(byteArray.getBytes(40, 4));

        return new IndexedTransaction(hash, blockHeight, (long) diskOffset, byteCount);
    }

    @Override
    public ByteArray valueToBytes(final IndexedTransaction value) {
        if (value == null) { return null; }

        final MutableByteArray byteArray = new MutableByteArray(44);
        byteArray.setBytes(0, value.hash);
        byteArray.setBytes(32, ByteUtil.integerToBytes(value.blockHeight));
        byteArray.setBytes(36, ByteUtil.integerToBytes(value.diskOffset));
        byteArray.setBytes(40, ByteUtil.integerToBytes(value.byteCount));
        return byteArray;
    }
}

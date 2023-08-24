package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.btreedb.BucketDb;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class IndexedTransactionEntryInflater implements BucketDb.BucketEntryInflater<Sha256Hash, IndexedTransaction> {
    @Override
    public Sha256Hash keyFromBytes(final ByteArray byteArray) {
        return Sha256Hash.wrap(byteArray.getBytes());
    }

    @Override
    public ByteArray keyToBytes(final Sha256Hash transactionHash) {
        return transactionHash;
    }

    @Override
    public int getKeyByteCount() {
        return Sha256Hash.BYTE_COUNT;
    }

    @Override
    public IndexedTransaction valueFromBytes(final ByteArray byteArray) {
        if (byteArray.isEmpty()) { return null; } // Support null values.
        final Long blockHeight = ByteUtil.bytesToLong(byteArray.getBytes(0, 4)); // NOTE: Only stored via 4 bytes.
        final int diskOffset = ByteUtil.bytesToInteger(byteArray.getBytes(4, 4));
        final int byteCount = ByteUtil.bytesToInteger(byteArray.getBytes(4, 4));

        return new IndexedTransaction(blockHeight, (long) diskOffset, byteCount);
    }

    @Override
    public ByteArray valueToBytes(final IndexedTransaction value) {
        if (value == null) { return new MutableByteArray(0); } // Support null values.

        final MutableByteArray byteArray = new MutableByteArray(8);
        byteArray.setBytes(0, ByteUtil.integerToBytes(value.blockHeight));
        byteArray.setBytes(4, ByteUtil.integerToBytes(value.diskOffset));
        return byteArray;
    }

    @Override
    public int getValueByteCount(final IndexedTransaction value) {
        if (value == null) { return 0; } // Support null values.
        return 8;
    }

    @Override
    public Sha256Hash getHash(final Sha256Hash sha256Hash) {
        return sha256Hash;
    }
}

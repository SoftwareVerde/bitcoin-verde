package com.softwareverde.bitcoin.server.module.node;

import com.google.leveldb.LevelDb;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class TransactionIdEntryInflater implements LevelDb.EntryInflater<Sha256Hash, Long> {
    @Override
    public Sha256Hash keyFromBytes(final ByteArray bytes) {
        return Sha256Hash.wrap(bytes.getBytes());
    }

    @Override
    public ByteArray keyToBytes(final Sha256Hash bytes) {
        return bytes;
    }

    @Override
    public Long valueFromBytes(final ByteArray bytes) {
        if (bytes == null) { return null; }
        if (bytes.isEmpty()) { return null; }
        return ByteUtil.bytesToLong(bytes);
    }

    @Override
    public ByteArray valueToBytes(final Long value) {
        if (value == null) { return null; }
        return MutableByteArray.wrap(ByteUtil.integerToBytes(value)); // NOTE: Only stored via 4 bytes.
    }

}

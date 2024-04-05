package com.softwareverde.bitcoin.server.module.node.store;

import com.google.leveldb.LevelDb;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class Sha256ByteArrayBucketEntryInflater implements LevelDb.EntryInflater<Sha256Hash, ByteArray> {

    @Override
    public Sha256Hash keyFromBytes(final ByteArray bytes) {
        return Sha256Hash.wrap(bytes.getBytes());
    }

    @Override
    public ByteArray keyToBytes(final Sha256Hash bytes) {
        return bytes;
    }

    @Override
    public ByteArray valueFromBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }
        if (byteArray.isEmpty()) { return null; }
        return byteArray;
    }

    @Override
    public ByteArray valueToBytes(final ByteArray blockBytes) {
        if (blockBytes.isEmpty()) { return null; }
        return blockBytes;
    }
}

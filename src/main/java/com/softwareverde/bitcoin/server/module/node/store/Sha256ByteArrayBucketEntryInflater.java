package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.btreedb.BucketDb;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class Sha256ByteArrayBucketEntryInflater implements BucketDb.BucketEntryInflater<Sha256Hash, ByteArray> {

    @Override
    public Sha256Hash getHash(final Sha256Hash bytes) {
        return bytes;
    }

    @Override
    public int getValueByteCount(final ByteArray blockBytes) {
        return blockBytes.getByteCount();
    }

    @Override
    public Sha256Hash keyFromBytes(final ByteArray bytes) {
        return Sha256Hash.wrap(bytes.getBytes());
    }

    @Override
    public ByteArray keyToBytes(final Sha256Hash bytes) {
        return bytes;
    }

    @Override
    public int getKeyByteCount() {
        return Sha256Hash.BYTE_COUNT;
    }

    @Override
    public ByteArray valueFromBytes(final ByteArray byteArray) {
        return byteArray;
    }

    @Override
    public ByteArray valueToBytes(final ByteArray blockBytes) {
        return blockBytes;
    }
}

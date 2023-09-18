package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.btreedb.BucketDb;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class TransactionIdEntryInflater implements BucketDb.BucketEntryInflater<Sha256Hash, Long> {
    @Override
    public Sha256Hash getHash(final Sha256Hash bytes) {
        return bytes;
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
    public Long valueFromBytes(final ByteArray bytes) {
        return ByteUtil.bytesToLong(bytes);
    }

    @Override
    public ByteArray valueToBytes(final Long value) {
        return MutableByteArray.wrap(ByteUtil.integerToBytes(value)); // NOTE: Only stored via 4 bytes.
    }

    @Override
    public int getValueByteCount(final Long value) {
        return 4; // NOTE: Only stored via 4 bytes.
    }
}

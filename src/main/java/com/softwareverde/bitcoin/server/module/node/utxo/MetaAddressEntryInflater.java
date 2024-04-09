package com.softwareverde.bitcoin.server.module.node.utxo;

import com.google.leveldb.LevelDb;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class MetaAddressEntryInflater implements LevelDb.EntryInflater<Sha256Hash, Integer> {
    @Override
    public Sha256Hash keyFromBytes(final ByteArray byteArray) {
        return Sha256Hash.wrap(byteArray.getBytes());
    }

    @Override
    public ByteArray keyToBytes(final Sha256Hash sha256Hash) {
        return sha256Hash;
    }

    @Override
    public Integer valueFromBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }
        if (byteArray.isEmpty()) { return null; }
        return ByteUtil.bytesToInteger(byteArray);
    }

    @Override
    public ByteArray valueToBytes(final Integer value) {
        if (value == null) { return null; }
        return MutableByteArray.wrap(new byte[] { value.byteValue() });
    }
}

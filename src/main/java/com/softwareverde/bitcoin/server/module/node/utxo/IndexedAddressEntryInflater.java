package com.softwareverde.bitcoin.server.module.node.utxo;

import com.google.leveldb.LevelDb;
import com.softwareverde.bitcoin.server.module.node.IndexedAddress;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class IndexedAddressEntryInflater implements LevelDb.EntryInflater<Sha256Hash, IndexedAddress> {
    @Override
    public Sha256Hash keyFromBytes(final ByteArray byteArray) {
        return Sha256Hash.wrap(byteArray.getBytes());
    }

    @Override
    public ByteArray keyToBytes(final Sha256Hash sha256Hash) {
        return sha256Hash;
    }

    @Override
    public IndexedAddress valueFromBytes(final ByteArray byteArray) {
        return IndexedAddress.fromBytes(byteArray);
    }

    @Override
    public ByteArray valueToBytes(final IndexedAddress indexedAddress) {
        return indexedAddress.getBytes();
    }
}

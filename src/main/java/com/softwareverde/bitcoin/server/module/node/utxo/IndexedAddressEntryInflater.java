package com.softwareverde.bitcoin.server.module.node.utxo;

import com.google.leveldb.LevelDb;
import com.softwareverde.bitcoin.server.module.node.indexing.DeflatedIndexedAddress;
import com.softwareverde.bitcoin.server.module.node.indexing.IndexedAddress;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class IndexedAddressEntryInflater implements LevelDb.EntryInflater<ByteArray, IndexedAddress> {
    @Override
    public ByteArray keyFromBytes(final ByteArray byteArray) {
        return byteArray;
    }

    @Override
    public ByteArray keyToBytes(final ByteArray byteArray) {
        return byteArray;
    }

    @Override
    public IndexedAddress valueFromBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }
        if (byteArray.isEmpty()) { return null; }
        return DeflatedIndexedAddress.fromBytes(byteArray);
    }

    @Override
    public ByteArray valueToBytes(final IndexedAddress indexedAddress) {
        if (indexedAddress == null) { return null; }
        return indexedAddress.getBytes();
    }
}

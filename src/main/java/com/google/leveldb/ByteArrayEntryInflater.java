package com.google.leveldb;

import com.softwareverde.constable.bytearray.ByteArray;

public class ByteArrayEntryInflater implements LevelDb.EntryInflater<ByteArray, ByteArray> {
    @Override
    public ByteArray keyFromBytes(final ByteArray bytes) {
        return bytes;
    }

    @Override
    public ByteArray keyToBytes(final ByteArray byteArray) {
        return byteArray;
    }

    @Override
    public ByteArray valueFromBytes(final ByteArray bytes) {
        if (bytes == null) { return null; }
        if (bytes.isEmpty()) { return null; }
        return bytes;
    }

    @Override
    public ByteArray valueToBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }
        if (byteArray.isEmpty()) { return null; }
        return byteArray;
    }
}

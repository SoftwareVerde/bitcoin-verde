package com.google.leveldb;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.StringUtil;

public class StringEntryInflater implements LevelDb.EntryInflater<String, String> {
    @Override
    public String keyFromBytes(final ByteArray bytes) {
        return StringUtil.bytesToString(bytes.getBytes());
    }

    @Override
    public ByteArray keyToBytes(final String string) {
        return MutableByteArray.wrap(StringUtil.stringToBytes(string));
    }

    @Override
    public String valueFromBytes(final ByteArray bytes) {
        if (bytes == null) { return null; }
        if (bytes.isEmpty()) { return null; }
        return StringUtil.bytesToString(bytes.getBytes());
    }

    @Override
    public ByteArray valueToBytes(final String string) {
        if (string == null) { return new MutableByteArray(0); } // Support null values.
        return MutableByteArray.wrap(StringUtil.stringToBytes(string));
    }
}

package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.btreedb.file.InputOutputFile;
import com.softwareverde.constable.UnsafeVisitor;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Tuple;

public class DiskKeyValueStore implements KeyValueStore, AutoCloseable {
    protected final InputOutputFile _inputOutputFile;
    protected final MutableHashMap<String, String> _values = new MutableHashMap<>();

    protected final byte[] _intBuffer = new byte[4];
    protected final byte[] _longBuffer = new byte[8];

    protected long _readLong() throws Exception {
        _inputOutputFile.read(_longBuffer);
        return ByteUtil.bytesToInteger(_longBuffer);
    }

    protected int _readInteger() throws Exception {
        _inputOutputFile.read(_intBuffer);
        return ByteUtil.bytesToInteger(_intBuffer);
    }

    protected String _readString() throws Exception {
        final int byteCount = _readInteger();
        final byte[] buffer = new byte[byteCount];
        _inputOutputFile.read(buffer);

        return StringUtil.bytesToString(buffer);
    }

    protected void _writeLong(final long value) throws Exception {
        final byte[] buffer = ByteUtil.longToBytes(value);
        _inputOutputFile.write(buffer);
    }

    protected void _writeInteger(final int value) throws Exception {
        final byte[] buffer = ByteUtil.integerToBytes(value);
        _inputOutputFile.write(buffer);
    }

    protected void _writeString(final String value) throws Exception {
        _writeInteger(value.length());
        final byte[] buffer = StringUtil.stringToBytes(value);
        _inputOutputFile.write(buffer);
    }

    public DiskKeyValueStore(final InputOutputFile inputOutputFile) {
        _inputOutputFile = inputOutputFile;
    }

    public synchronized void open() throws Exception {
        _values.clear();

        _inputOutputFile.open();
        try {
            if (! _inputOutputFile.exists()) { return; }
            if (_inputOutputFile.getByteCount() < _intBuffer.length) { return; }

            final int entryCount = _readInteger();
            for (int i = 0; i < entryCount; ++i) {
                final String key = _readString();
                final String value = _readString();

                _values.put(key, value);
            }
        }
        finally {
            _inputOutputFile.close();
        }
    }

    @Override
    public synchronized void close() throws Exception {
        _inputOutputFile.open();
        try {
            _inputOutputFile.truncate();

            final int entryCount = _values.getCount();
            _writeInteger(entryCount);

            _values.visit(new UnsafeVisitor<>() {
                @Override
                public boolean run(final Tuple<String, String> value) throws Exception {
                    _writeString(value.first);
                    _writeString(value.second);
                    return true;
                }
            });
        }
        finally {
            _inputOutputFile.close();
        }
    }

    @Override
    public synchronized String getString(final String key) {
        return _values.get(key);
    }

    @Override
    public synchronized void putString(final String key, final String value) {
        _values.put(key, value);
    }

    @Override
    public synchronized Boolean hasKey(final String key) {
        return _values.containsKey(key);
    }

    @Override
    public synchronized void removeKey(final String key) {
        _values.remove(key);
    }

    @Override
    public synchronized void clear() {
        _values.clear();
    }
}

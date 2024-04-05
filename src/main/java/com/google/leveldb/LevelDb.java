package com.google.leveldb;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.jni.NativeUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LevelDb<Key, Value> implements AutoCloseable {
    public interface EntryInflater<Key, Value> {
        Key keyFromBytes(ByteArray bytes);
        ByteArray keyToBytes(Key key);

        Value valueFromBytes(ByteArray bytes);
        ByteArray valueToBytes(Value value);
    }

    private static boolean _libraryLoadedCorrectly = false;

    public static boolean isEnabled() {
        return _libraryLoadedCorrectly;
    }

    public static void loadLibrary() {
        if (_libraryLoadedCorrectly) { return; }

        boolean isEnabled = true;
        try {
            NativeUtil.loadLibraryFromJar("/lib/libleveldb.1.23.0.dylib");
            NativeUtil.loadLibraryFromJar("/lib/leveldb-jni.dylib");
        }
        catch (final Throwable exception) {
            Logger.debug("NOTICE: leveldb failed to load.", exception);
            isEnabled = false;
        }
        _libraryLoadedCorrectly = isEnabled;
    }

    protected static final ThreadLocal<ByteBuffer> _nativeKeyBuffer = new ThreadLocal<>();
    protected static final ThreadLocal<ByteBuffer> _nativeValueBuffer = new ThreadLocal<>();
    protected static ByteBuffer _getByteBuffer(final ByteArray data, final ThreadLocal<ByteBuffer> threadLocal) {
        final int dataByteCount = (data != null ? data.getByteCount() : 0);
        ByteBuffer byteBuffer = threadLocal.get();
        if ((byteBuffer == null) || (byteBuffer.capacity() < dataByteCount)) {
            byteBuffer = ByteBuffer.allocateDirect(dataByteCount);
            byteBuffer.order(ByteOrder.nativeOrder());
            threadLocal.set(byteBuffer);
        }

        byteBuffer.rewind();
        for (int i = 0; i < dataByteCount; ++i) {
            byteBuffer.put(i, data.getByte(i));
        }

        return byteBuffer;
    }

    protected final EntryInflater<Key, Value> _entryInflater;

    protected final File _directory;
    protected long _dbPointer = 0L;

    public LevelDb(final File directory, final EntryInflater<Key, Value> entryInflater) {
        _directory = directory;
        _entryInflater = entryInflater;
    }

    public void open() {
        LevelDb.loadLibrary();

        final long options = com.google.leveldb.NativeLevelDb.leveldb_options_create();
        com.google.leveldb.NativeLevelDb.leveldb_options_set_create_if_missing(options, true);

        final String absolutePath = _directory.getAbsolutePath();
        _dbPointer = com.google.leveldb.NativeLevelDb.leveldb_open(options, absolutePath);
        com.google.leveldb.NativeLevelDb.leveldb_options_destroy(options);

        Logger.debug("LevelDb: " + absolutePath);
    }

    public void put(final Key key, final Value value) {
        final long writeOptions = com.google.leveldb.NativeLevelDb.leveldb_writeoptions_create();
        com.google.leveldb.NativeLevelDb.leveldb_writeoptions_set_sync(writeOptions, false);

        final ByteArray keyBytes = _entryInflater.keyToBytes(key);
        final ByteBuffer keyBuffer = _getByteBuffer(keyBytes, _nativeKeyBuffer);
        final long keyByteCount = keyBytes.getByteCount();

        final ByteArray valueBytes = _entryInflater.valueToBytes(value);
        final ByteBuffer valueBuffer = _getByteBuffer(valueBytes, _nativeValueBuffer);
        final long valueByteCount = (valueBytes != null ? valueBytes.getByteCount() : 0);

        com.google.leveldb.NativeLevelDb.leveldb_put(_dbPointer, writeOptions, keyBuffer, keyByteCount, valueBuffer, valueByteCount);
        com.google.leveldb.NativeLevelDb.leveldb_writeoptions_destroy(writeOptions);
    }

    public Value get(final Key key) {
        long readOptions = com.google.leveldb.NativeLevelDb.leveldb_readoptions_create();
        com.google.leveldb.NativeLevelDb.leveldb_readoptions_set_verify_checksums(readOptions, false);
        com.google.leveldb.NativeLevelDb.leveldb_readoptions_set_fill_cache(readOptions, false);

        final ByteArray keyBytes = _entryInflater.keyToBytes(key);
        final ByteBuffer keyBuffer = _getByteBuffer(keyBytes, _nativeKeyBuffer);
        final long keyByteCount = keyBytes.getByteCount();

        final ByteArray value = MutableByteArray.wrap(com.google.leveldb.NativeLevelDb.leveldb_get(_dbPointer, readOptions, keyBuffer, keyByteCount));
        com.google.leveldb.NativeLevelDb.leveldb_readoptions_destroy(readOptions);

        return _entryInflater.valueFromBytes(value);
    }

    public boolean containsKey(final Key key) {
        long readOptions = com.google.leveldb.NativeLevelDb.leveldb_readoptions_create();
        com.google.leveldb.NativeLevelDb.leveldb_readoptions_set_verify_checksums(readOptions, false);
        com.google.leveldb.NativeLevelDb.leveldb_readoptions_set_fill_cache(readOptions, false);

        final ByteArray keyBytes = _entryInflater.keyToBytes(key);
        final ByteBuffer keyBuffer = _getByteBuffer(keyBytes, _nativeKeyBuffer);
        final long keyByteCount = keyBytes.getByteCount();

        final ByteArray value = MutableByteArray.wrap(com.google.leveldb.NativeLevelDb.leveldb_get(_dbPointer, readOptions, keyBuffer, keyByteCount));
        com.google.leveldb.NativeLevelDb.leveldb_readoptions_destroy(readOptions);

        return (! value.isEmpty());
    }

    public void remove(final Key key) {
        final long writeOptions = com.google.leveldb.NativeLevelDb.leveldb_writeoptions_create();
        com.google.leveldb.NativeLevelDb.leveldb_writeoptions_set_sync(writeOptions, false);

        final ByteArray keyBytes = _entryInflater.keyToBytes(key);
        final ByteBuffer keyBuffer = _getByteBuffer(keyBytes, _nativeKeyBuffer);
        final long keyByteCount = keyBytes.getByteCount();

        com.google.leveldb.NativeLevelDb.leveldb_delete(_dbPointer, writeOptions, keyBuffer, keyByteCount);

        com.google.leveldb.NativeLevelDb.leveldb_writeoptions_destroy(writeOptions);
    }

    @Override
    public void close() {
        com.google.leveldb.NativeLevelDb.leveldb_close(_dbPointer);
        _dbPointer = 0L;
    }

    public int getStagedWriteCount() {
        return 0; // TODO
    }

    public void delete() {
        // TODO
    }

    public void commit() {
        // TODO
    }

    public void flush() {
        // TODO
    }

    public boolean isOpen() {
        return _dbPointer != 0L;
    }

    public File getDirectory() {
        return _directory;
    }
}

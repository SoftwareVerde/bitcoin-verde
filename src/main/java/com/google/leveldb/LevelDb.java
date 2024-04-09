package com.google.leveldb;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.jni.NativeUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    protected final ConcurrentLinkedQueue<Buffer> _buffers = new ConcurrentLinkedQueue<>();
    protected class Buffer implements AutoCloseable {
        protected final int _byteCount;
        protected final ByteBuffer _byteBuffer;

        Buffer(final int byteCount) {
            _byteCount = byteCount;
            _byteBuffer = ByteBuffer.allocateDirect(byteCount);
            _byteBuffer.order(ByteOrder.nativeOrder());
        }

        public ByteBuffer getByteBuffer() {
            return _byteBuffer;
        }

        public int getByteCount() {
            return _byteCount;
        }

        @Override
        public void close() {
            _buffers.add(this);
        }
    }

    protected int _getBufferSize(final int dataByteCount) {
        final long baseBufferSize = 16L * ByteUtil.Unit.Binary.KIBIBYTES;
        final long newByteBufferKb = (dataByteCount + (baseBufferSize - 1L)) / baseBufferSize;
        return (int) (baseBufferSize * newByteBufferKb);
    }

    protected Buffer _getBuffer(final int minByteCount) {
        while (true) {
            final Buffer existingBuffer = _buffers.poll();
            if (existingBuffer == null) { break; }
            if (existingBuffer.getByteCount() >= minByteCount) {
                return existingBuffer;
            }
        }

        final int newBufferSize = _getBufferSize(minByteCount);
        Logger.debug("Allocated: " + newBufferSize);
        return new Buffer(newBufferSize);
    }

    protected Buffer _getBuffer(final ByteArray data) {
        final int dataByteCount = data.getByteCount();
        final Buffer buffer = _getBuffer(dataByteCount);

        final ByteBuffer byteBuffer = buffer.getByteBuffer();
        byteBuffer.rewind();
        for (int i = 0; i < dataByteCount; ++i) {
            byteBuffer.put(i, data.getByte(i));
        }
        return buffer;
    }

//    protected static final ThreadLocal<ByteBuffer> _nativeKeyBuffer = new ThreadLocal<>();
//    protected static final ThreadLocal<ByteBuffer> _nativeValueBuffer = new ThreadLocal<>();
//    protected static ByteBuffer getByteBuffer(final ByteArray data, final ThreadLocal<ByteBuffer> threadLocal) {
//        final int dataByteCount = (data != null ? data.getByteCount() : 0);
//        ByteBuffer byteBuffer = threadLocal.get();
//        if ((byteBuffer == null) || (byteBuffer.capacity() < dataByteCount)) {
//            final long baseBufferSize = 16L * ByteUtil.Unit.Binary.KIBIBYTES;
//            final long newByteBufferKb = (dataByteCount + (baseBufferSize - 1L)) / baseBufferSize;
//            final int newBufferByteCount = (int) (baseBufferSize * newByteBufferKb);
//
//            byteBuffer = ByteBuffer.allocateDirect(newBufferByteCount);
//            byteBuffer.order(ByteOrder.nativeOrder());
//            threadLocal.set(byteBuffer);
//            System.out.println("Allocated: " + dataByteCount + " (" + newBufferByteCount + ")");
//        }
//
//        byteBuffer.rewind();
//        for (int i = 0; i < dataByteCount; ++i) {
//            byteBuffer.put(i, data.getByte(i));
//        }
//
//        return byteBuffer;
//    }

    protected final EntryInflater<Key, Value> _entryInflater;

    protected final File _directory;
    protected long _dbPointer = 0L;
    protected long _dbCachePointer = 0L;
    protected long _writeOptionsPointer = 0L;
    protected long _readOptionsPointer = 0L;
    protected long _cacheReadOptionsPointer = 0L;

    public LevelDb(final File directory, final EntryInflater<Key, Value> entryInflater) {
        _directory = directory;
        _entryInflater = entryInflater;
    }

    public void open() {
        this.open(null, null);
    }
    public void open(final Long cacheByteCount, final Long writeBufferByteCount) {
        LevelDb.loadLibrary();

        final long options = com.google.leveldb.NativeLevelDb.leveldb_options_create();
        com.google.leveldb.NativeLevelDb.leveldb_options_set_create_if_missing(options, true);

        if (cacheByteCount != null) {
            _dbCachePointer = com.google.leveldb.NativeLevelDb.leveldb_cache_create_lru(cacheByteCount);
            com.google.leveldb.NativeLevelDb.leveldb_options_set_cache(options, _dbCachePointer);
        }

        if (writeBufferByteCount != null) {
            com.google.leveldb.NativeLevelDb.leveldb_options_set_write_buffer_size(options, writeBufferByteCount);
        }

        final String absolutePath = _directory.getAbsolutePath();
        _dbPointer = com.google.leveldb.NativeLevelDb.leveldb_open(options, absolutePath);
        com.google.leveldb.NativeLevelDb.leveldb_options_destroy(options);

        _writeOptionsPointer = com.google.leveldb.NativeLevelDb.leveldb_writeoptions_create();
        com.google.leveldb.NativeLevelDb.leveldb_writeoptions_set_sync(_writeOptionsPointer, false);

        _readOptionsPointer = com.google.leveldb.NativeLevelDb.leveldb_readoptions_create();
        com.google.leveldb.NativeLevelDb.leveldb_readoptions_set_verify_checksums(_readOptionsPointer, false);

        _cacheReadOptionsPointer = com.google.leveldb.NativeLevelDb.leveldb_readoptions_create();
        com.google.leveldb.NativeLevelDb.leveldb_readoptions_set_verify_checksums(_cacheReadOptionsPointer, false);
        com.google.leveldb.NativeLevelDb.leveldb_readoptions_set_fill_cache(_cacheReadOptionsPointer, true);

        Logger.debug("LevelDb: " + absolutePath);
    }

    public void put(final Key key, final Value value) {
        final ByteArray keyBytes = _entryInflater.keyToBytes(key);
        final ByteArray valueBytes = _entryInflater.valueToBytes(value);
        final long keyByteCount = keyBytes.getByteCount();
        final long valueByteCount = (valueBytes != null ? valueBytes.getByteCount() : 0);

        try (
            final Buffer keyBuffer = _getBuffer(keyBytes);
            final Buffer valueBuffer = _getBuffer(valueBytes)
        ) {
            final ByteBuffer keyByteBuffer = keyBuffer.getByteBuffer();
            final ByteBuffer valueByteBuffer = valueBuffer.getByteBuffer();

            com.google.leveldb.NativeLevelDb.leveldb_put(_dbPointer, _writeOptionsPointer, keyByteBuffer, keyByteCount, valueByteBuffer, valueByteCount);
        }
    }

    public Value get(final Key key) {
        final ByteArray value;
        final ByteArray keyBytes = _entryInflater.keyToBytes(key);
        final long keyByteCount = keyBytes.getByteCount();
        try (final Buffer keyBuffer = _getBuffer(keyBytes)) {
            final ByteBuffer keyByteBuffer = keyBuffer.getByteBuffer();

            value = MutableByteArray.wrap(com.google.leveldb.NativeLevelDb.leveldb_get(_dbPointer, _readOptionsPointer, keyByteBuffer, keyByteCount));
        }

        return _entryInflater.valueFromBytes(value);
    }

    public boolean containsKey(final Key key) {
        final ByteArray keyBytes = _entryInflater.keyToBytes(key);
        final long keyByteCount = keyBytes.getByteCount();
        try (final Buffer keyBuffer = _getBuffer(keyBytes)) {
            final ByteBuffer keyByteBuffer = keyBuffer.getByteBuffer();

            final ByteArray value = MutableByteArray.wrap(com.google.leveldb.NativeLevelDb.leveldb_get(_dbPointer, _readOptionsPointer, keyByteBuffer, keyByteCount));

            return (! value.isEmpty());
        }
    }

    public void remove(final Key key) {
        final ByteArray keyBytes = _entryInflater.keyToBytes(key);
        final long keyByteCount = keyBytes.getByteCount();

        try (final Buffer keyBuffer = _getBuffer(keyBytes)) {
            final ByteBuffer keyByteBuffer = keyBuffer.getByteBuffer();

            com.google.leveldb.NativeLevelDb.leveldb_delete(_dbPointer, _writeOptionsPointer, keyByteBuffer, keyByteCount);
        }
    }

    @Override
    public void close() {
        if (_cacheReadOptionsPointer != 0L) {
            com.google.leveldb.NativeLevelDb.leveldb_readoptions_destroy(_cacheReadOptionsPointer);
            _cacheReadOptionsPointer = 0L;
        }

        if (_readOptionsPointer != 0L) {
            com.google.leveldb.NativeLevelDb.leveldb_readoptions_destroy(_readOptionsPointer);
            _readOptionsPointer = 0L;
        }

        if (_writeOptionsPointer != 0L) {
            com.google.leveldb.NativeLevelDb.leveldb_writeoptions_destroy(_writeOptionsPointer);
            _writeOptionsPointer = 0L;
        }

        if (_dbPointer != 0L) {
            com.google.leveldb.NativeLevelDb.leveldb_close(_dbPointer);
            _dbPointer = 0L;
        }

        if (_dbCachePointer != 0L) {
            com.google.leveldb.NativeLevelDb.leveldb_cache_destroy(_dbCachePointer);
            _dbCachePointer = 0L;
        }
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

package com.softwareverde.btreedb.file;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class MemoryMappedInputOutputFile implements InputOutputFile {

    protected final MemoryMappedData _data;
    protected final AtomicBoolean _isOpen = new AtomicBoolean(false);
    protected long _position = 0L;

    public MemoryMappedInputOutputFile(final File file) {
        _data = new FullyMemoryMappedData(file);
    }

    public MemoryMappedInputOutputFile(final MemoryMappedData data) {
        _data = data;
    }

    @Override
    public byte read() throws Exception {
        if (! _isOpen.get()) { throw new Exception("File closed."); }
        final byte[] buffer = new byte[1];
        _data.doRead(_position, buffer, 0, buffer.length);
        _position += buffer.length;
        return buffer[0];
    }

    @Override
    public byte[] read(final int byteCount) throws Exception {
        if (! _isOpen.get()) { throw new Exception("File closed."); }
        final byte[] buffer = new byte[byteCount];
        _data.doRead(_position, buffer, 0, buffer.length);
        _position += buffer.length;
        return buffer;
    }

    @Override
    public void read(final byte[] buffer) throws Exception {
        if (! _isOpen.get()) { throw new Exception("File closed."); }
        _data.doRead(_position, buffer, 0, buffer.length);
        _position += buffer.length;
    }

    @Override
    public void read(final byte[] buffer, final int startPosition, final int byteCount) throws Exception {
        if (! _isOpen.get()) { throw new Exception("File closed."); }
        _data.doRead(_position, buffer, startPosition, byteCount);
        _position += byteCount;
    }

    @Override
    public void truncate() throws Exception {
        if (! _isOpen.get()) { throw new Exception("File closed."); }
        _data.truncate();
        _position = 0L;
    }

    @Override
    public void write(final byte b) throws Exception {
        if (! _isOpen.get()) { throw new Exception("File closed."); }
        final byte[] buffer = new byte[] { b };
        _data.doWrite(_position, buffer, 0, buffer.length);
        _position += buffer.length;
    }

    @Override
    public void write(final byte[] buffer) throws Exception {
        if (! _isOpen.get()) { throw new Exception("File closed."); }
        _data.doWrite(_position, buffer, 0, buffer.length);
        _position += buffer.length;
    }

    @Override
    public void write(final byte[] buffer, final int bufferOffset, final int byteCount) throws Exception {
        if (! _isOpen.get()) { throw new Exception("File closed."); }
        _data.doWrite(_position, buffer, bufferOffset, byteCount);
        _position += byteCount;
    }

    @Override
    public boolean isOpen() {
        return _isOpen.get();
    }

    @Override
    public void open() throws Exception {
        if (! _isOpen.compareAndSet(false, true)) { return; }
        _data.open();
    }

    @Override
    public long getByteCount() throws Exception {
        return _data.getByteCount();
    }

    @Override
    public long getPosition() throws Exception {
        return _position;
    }

    @Override
    public void setPosition(final long position) throws Exception {
        _position = position;
    }

    @Override
    public void close() throws Exception {
        if (! _isOpen.compareAndSet(true, false)) { return; }
        _data.close();
    }

    @Override
    public String getName() {
        return _data.getFile().getName();
    }

    @Override
    public boolean exists() {
        return _data.getFile().exists();
    }

    @Override
    public void delete() throws Exception {
        _data.getFile().delete();
    }
}

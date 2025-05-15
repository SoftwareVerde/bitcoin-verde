package com.softwareverde.btreedb.file;

import com.softwareverde.constable.bytearray.ByteArray;

public class ByteArrayInputFile implements InputFile {
    protected final String _name;
    protected final ByteArray _byteArray;
    protected int _position = 0;
    protected boolean _isOpen;

    public ByteArrayInputFile(final String name, final ByteArray byteArray) {
        _name = name;
        _byteArray = byteArray;
    }

    @Override
    public byte read() throws Exception {
        final int position = _position;
        _position += 1;
        if (_position > _byteArray.getByteCount()) { throw new Exception("EOF"); }

        return _byteArray.getByte(position);
    }

    @Override
    public byte[] read(final int byteCount) throws Exception {
        final int position = _position;
        _position += byteCount;
        if (_position > _byteArray.getByteCount()) { throw new Exception("EOF"); }

        return _byteArray.getBytes(position, byteCount);
    }

    @Override
    public void read(final byte[] buffer) throws Exception {
        final int position = _position;
        _position += buffer.length;
        if (_position > _byteArray.getByteCount()) { throw new Exception("EOF"); }

        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = _byteArray.getByte(position + i);
        }
    }

    @Override
    public void read(final byte[] buffer, final int startPosition, final int byteCount) throws Exception {
        final int position = _position;
        _position += byteCount;
        if (_position > _byteArray.getByteCount()) { throw new Exception("EOF"); }

        for (int i = 0; i < byteCount; ++i) {
            buffer[startPosition + i] = _byteArray.getByte(position + i);
        }
    }

    @Override
    public boolean isOpen() throws Exception {
        return _isOpen;
    }

    @Override
    public void open() throws Exception {
        if (_isOpen) { return; }

        _isOpen = true;
        _position = 0;
    }

    @Override
    public long getByteCount() throws Exception {
        return _byteArray.getByteCount();
    }

    @Override
    public long getPosition() throws Exception {
        return _position;
    }

    @Override
    public void setPosition(final long position) throws Exception {
        if (position >= Integer.MAX_VALUE) {
            _position = Integer.MAX_VALUE;
            return;
        }

        _position = (int) position;
    }

    @Override
    public void close() throws Exception {
        _isOpen = false;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public void delete() throws Exception {
        // Nothing.
    }
}

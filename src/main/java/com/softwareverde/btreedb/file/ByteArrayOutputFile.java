package com.softwareverde.btreedb.file;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class ByteArrayOutputFile implements OutputFile {
    protected final AtomicBoolean _isOpen = new AtomicBoolean(false);
    protected ByteArrayOutputStream _byteArrayOutputStream;
    protected String _name;
    protected long _position = 0L;

    @Override
    public void truncate() throws Exception {
        _byteArrayOutputStream.reset();
        _position = 0L;
    }

    @Override
    public void write(final byte b) throws Exception {
        this.write(new byte[] { b }, 0, 1);
    }

    @Override
    public void write(final byte[] buffer) throws Exception {
        this.write(buffer, 0, buffer.length);
    }

    @Override
    public void write(final byte[] buffer, final int bufferOffset, final int byteCount) throws Exception {
        if (_position > _byteArrayOutputStream.size()) {
            final byte[] filler = new byte[(int) (_position - _byteArrayOutputStream.size())];
            _byteArrayOutputStream.write(filler);
            _position += filler.length;
        }

        if (_position == _byteArrayOutputStream.size()) {
            _byteArrayOutputStream.write(buffer, bufferOffset, byteCount);
            _position += byteCount;
        }
        else { // _position < _byteArrayOutputStream.size()
            final byte[] bytes = _byteArrayOutputStream.toByteArray();
            _byteArrayOutputStream.reset();
            _byteArrayOutputStream.write(bytes, 0, (int) _position);
            _byteArrayOutputStream.write(buffer, bufferOffset, byteCount);
            _position += byteCount;
            final int remainingByteCount = (int) (bytes.length - _position);
            if (remainingByteCount > 0) {
                _byteArrayOutputStream.write(bytes, (int) _position, remainingByteCount);
            }
        }
    }

    @Override
    public boolean isOpen() throws Exception {
        return _isOpen.get();
    }

    @Override
    public void open() throws Exception {
        if (! _isOpen.compareAndSet(false, true)) { return; }
        _byteArrayOutputStream = new ByteArrayOutputStream();
    }

    @Override
    public long getByteCount() throws Exception {
        return _byteArrayOutputStream.size();
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
        _position = 0L;
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
    public void delete() {
        _byteArrayOutputStream.reset();
        _position = 0L;
    }

    public void setName(final String name) {
        _name = name;
    }

    public byte[] getBytes() {
        return _byteArrayOutputStream.toByteArray();
    }
}

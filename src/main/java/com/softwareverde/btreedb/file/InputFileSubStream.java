package com.softwareverde.btreedb.file;

public class InputFileSubStream implements InputFile {
    protected final InputFile _inputFile;
    protected final long _physicalPosition;
    protected final long _byteCount;
    protected long _virtualPosition = 0L;

    protected final String _name;
    protected boolean _isOpen = true;

    public InputFileSubStream(final String name, final InputFile inputFile, final long physicalPosition, final long byteCount) {
        _name = name;
        _inputFile = inputFile;
        _physicalPosition = physicalPosition;
        _byteCount = byteCount;
    }

    @Override
    public byte read() throws Exception {
        _virtualPosition += 1L;
        if (_virtualPosition > _byteCount) { throw new Exception("EOF"); }

        return _inputFile.read();
    }

    @Override
    public byte[] read(final int byteCount) throws Exception {
        _virtualPosition += byteCount;
        if (_virtualPosition > _byteCount) { throw new Exception("EOF"); }

        return _inputFile.read(byteCount);
    }

    @Override
    public void read(final byte[] buffer) throws Exception {
        _virtualPosition += buffer.length;
        if (_virtualPosition > _byteCount) { throw new Exception("EOF"); }

        _inputFile.read(buffer);
    }

    @Override
    public void read(final byte[] buffer, final int startPosition, final int byteCount) throws Exception {
        _virtualPosition += byteCount;
        if (_virtualPosition > _byteCount) { throw new Exception("EOF"); }

        _inputFile.read(buffer, startPosition, byteCount);
    }

    @Override
    public boolean isOpen() throws Exception {
        return _isOpen;
    }

    @Override
    public void open() throws Exception {
        _inputFile.setPosition(_physicalPosition);
        _isOpen = true;
    }

    @Override
    public long getByteCount() throws Exception {
        return _byteCount;
    }

    @Override
    public long getPosition() throws Exception {
        return _virtualPosition;
    }

    @Override
    public void setPosition(final long position) throws Exception {
        _virtualPosition = position;
        _inputFile.setPosition(_physicalPosition + _virtualPosition);
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

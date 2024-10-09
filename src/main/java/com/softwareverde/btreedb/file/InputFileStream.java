package com.softwareverde.btreedb.file;

import com.softwareverde.util.ByteUtil;

import java.io.IOException;
import java.io.InputStream;

public class InputFileStream extends InputStream {
    protected final InputFile _inputFile;

    public InputFileStream(final InputFile inputFile) {
        _inputFile = inputFile;
    }

    @Override
    public int read() throws IOException {
        try {
            final byte value = _inputFile.read();
            return ByteUtil.byteToInteger(value);
        }
        catch (final Exception exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        try {
            final long position = _inputFile.getPosition();
            final long byteCount = _inputFile.getByteCount();
            final long byteCountRemaining = (byteCount - position);
            if (byteCountRemaining <= 0) { return -1; }

            final int readByteCount = Math.min((int) byteCountRemaining, len);
            _inputFile.read(b, off, readByteCount);
            return readByteCount;
        }
        catch (final Exception exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public int available() throws IOException {
        try {
            final long byteCount = _inputFile.getByteCount();
            final long position = _inputFile.getPosition();
            return (int) (byteCount - position);
        }
        catch (final Exception exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public long skip(final long n) throws IOException {
        try {
            final long position = _inputFile.getPosition();
            _inputFile.setPosition(position + n);
            return n;
        }
        catch (final Exception exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            _inputFile.close();
        }
        catch (final Exception exception) {
            throw new IOException(exception);
        }
    }
}

package com.softwareverde.btreedb.file;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class NioInputOutputFile implements InputOutputFile {
    protected final File _file;
    protected RandomAccessFile _randomAccessFile;
    protected FileChannel _fileChannel;

    protected final byte[] _byteBuffer = new byte[1];

    public NioInputOutputFile(final File file) {
        _file = file;
    }

    @Override
    public synchronized byte read() throws Exception {
        this.read(_byteBuffer);
        return _byteBuffer[0];
    }

    @Override
    public synchronized byte[] read(final int byteCount) throws Exception {
        final byte[] buffer = new byte[byteCount];
        this.read(buffer);
        return buffer;
    }

    @Override
    public synchronized void read(final byte[] buffer) throws Exception {
        this.read(buffer, 0, buffer.length);
    }

    @Override
    public synchronized void read(final byte[] buffer, final int startPosition, final int byteCount) throws Exception {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, startPosition, byteCount);

        int readByteCount = 0;
        do {
            final int readResult = _fileChannel.read(byteBuffer);
            if (readResult < 0) { throw new Exception("EOF"); }
            readByteCount += readResult;
        } while (readByteCount != byteCount);
    }

    public synchronized void read(final ByteBuffer byteBuffer, final int startPosition, final int byteCount) throws Exception {
        byteBuffer.position(startPosition);
        final int originalLimit = byteBuffer.limit();
        byteBuffer.limit(byteCount);

        try {
            int readByteCount = 0;
            do {
                final int readResult = _fileChannel.read(byteBuffer);
                if (readResult < 0) { throw new Exception("EOF"); }
                readByteCount += readResult;
            } while (readByteCount != byteCount);
        }
        finally {
            byteBuffer.limit(originalLimit);
        }
    }

    @Override
    public synchronized void truncate() throws Exception {
        _fileChannel.truncate(0L);
    }

    @Override
    public synchronized void write(final byte b) throws Exception {
        _byteBuffer[0] = b;
        this.write(_byteBuffer);
    }

    @Override
    public synchronized void write(final byte[] buffer) throws Exception {
        this.write(buffer, 0, buffer.length);
    }

    @Override
    public synchronized void write(final byte[] buffer, final int bufferOffset, final int byteCount) throws Exception {
        int writeByteCount = 0;
        do {
            writeByteCount += _fileChannel.write(ByteBuffer.wrap(buffer, bufferOffset + writeByteCount, byteCount - writeByteCount));
        } while (writeByteCount != byteCount);
    }

    @Override
    public synchronized boolean isOpen() throws Exception {
        return _fileChannel != null;
    }

    @Override
    public synchronized void open() throws Exception {
        _randomAccessFile = new RandomAccessFile(_file, "rw");
        _fileChannel = _randomAccessFile.getChannel();
        _fileChannel.position(0L);
    }

    @Override
    public synchronized long getByteCount() throws Exception {
        return _fileChannel.size();
    }

    @Override
    public synchronized long getPosition() throws Exception {
        return _fileChannel.position();
    }

    @Override
    public synchronized void setPosition(final long position) throws Exception {
        _fileChannel.position(position);
    }

    @Override
    public void close() throws Exception {
        _randomAccessFile.getFD().sync();

        _fileChannel.close();
        _randomAccessFile.close();

        _fileChannel = null;
        _randomAccessFile = null;
    }

    @Override
    public String getName() {
        return _file.getName();
    }

    @Override
    public boolean exists() {
        return _file.exists();
    }

    @Override
    public void delete() throws Exception {
        _file.delete();
    }
}

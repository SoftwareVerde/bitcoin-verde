package com.softwareverde.btreedb.file;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.function.Supplier;

public class InputOutputFileCore implements InputOutputFile {
    public enum Mode {
        READ, READ_WRITE, READ_WRITE_S, READ_WRITE_D
    }

    protected static String getModeString(final Mode mode) {
        switch (mode) {
            case READ: return "r";
            case READ_WRITE: return "rw";
            case READ_WRITE_D: return "rwd";
            case READ_WRITE_S: return "rws";
        }
        return null;
    }

    protected final File _file;
    protected final Mode _mode;
    protected final byte[] _singleByteBuffer = new byte[1];
    protected RandomAccessFile _randomAccessFile;

    public InputOutputFileCore(final File file) {
        this(file, Mode.READ_WRITE);
    }

    public InputOutputFileCore(final File file, final Mode mode) {
        _file = file;
        _mode = mode;

        final String modeString = InputOutputFileCore.getModeString(mode);
        if (modeString == null) { throw new RuntimeException("Unsupported mode type."); }
    }

    @Override
    public boolean isOpen() throws Exception {
        return (_randomAccessFile != null);
    }

    @Override
    public void open() throws Exception {
        if (_randomAccessFile != null) { return; }

        final String modeString = InputOutputFileCore.getModeString(_mode);
        _randomAccessFile = new RandomAccessFile(_file, modeString);
    }

    @Override
    public void read(final byte[] buffer) throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        _randomAccessFile.readFully(buffer);
    }

    @Override
    public void read(final byte[] buffer, final int startPosition, final int byteCount) throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        _randomAccessFile.readFully(buffer, startPosition, byteCount);
    }

    @Override
    public byte read() throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        _randomAccessFile.readFully(_singleByteBuffer);
        return _singleByteBuffer[0];
    }

    @Override
    public byte[] read(final int byteCount) throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        final byte[] bytes = new byte[byteCount];
        _randomAccessFile.readFully(bytes);
        return bytes;
    }

    @Override
    public void truncate() throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        _randomAccessFile.setLength(0L);
        _randomAccessFile.seek(0L);
    }

    @Override
    public void write(final byte b) throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        _singleByteBuffer[0] = b;
        _randomAccessFile.write(_singleByteBuffer);
    }

    @Override
    public void write(final byte[] buffer) throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        _randomAccessFile.write(buffer);
    }

    @Override
    public void write(byte[] buffer, int bufferOffset, int byteCount) throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        _randomAccessFile.write(buffer, bufferOffset, byteCount);
    }

    @Override
    public long getByteCount() throws Exception {
        if (_randomAccessFile == null) {
            return _file.length();
        }

        return _randomAccessFile.length();
    }

    @Override
    public long getPosition() throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        return _randomAccessFile.getFilePointer();
    }

    @Override
    public void setPosition(final long position) throws Exception {
        if (_randomAccessFile == null) { throw new Exception("File not open."); }
        _randomAccessFile.seek(position);
    }

    @Override
    public void close() throws Exception {
        if (_randomAccessFile == null) { return; }

        _randomAccessFile.close();
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
    public void delete() {
        _file.delete();
    }
}

package com.softwareverde.btreedb.file;

public interface InputFile extends SeekFile {
    byte read() throws Exception;
    byte[] read(int byteCount) throws Exception;

    void read(byte[] buffer) throws Exception;
    void read(byte[] buffer, int startPosition, int byteCount) throws Exception;
}

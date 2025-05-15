package com.softwareverde.btreedb.file;

public interface OutputFile extends SeekFile {
    void truncate() throws Exception;
    void write (byte b) throws Exception;
    void write(byte[] buffer) throws Exception;
    void write(byte[] buffer, int bufferOffset, int byteCount) throws Exception;
}

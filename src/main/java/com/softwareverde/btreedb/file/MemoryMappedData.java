package com.softwareverde.btreedb.file;

import java.io.File;

public interface MemoryMappedData extends AutoCloseable {
    void open() throws Exception;

    @Override
    void close() throws Exception;

    void doRead(long position, final byte[] buffer, int bufferOffset, int byteCountRemaining) throws Exception;

    void doWrite(long position, final byte[] buffer, int bufferOffset, int byteCountRemaining) throws Exception;

    void truncate() throws Exception;

    long getByteCount() throws Exception;

    File getFile();
}
package com.softwareverde.btreedb.file;

public interface SeekFile extends AutoCloseable {
    boolean isOpen() throws Exception;
    void open() throws Exception;
    long getByteCount() throws Exception;
    long getPosition() throws Exception;
    void setPosition(long position) throws Exception;

    @Override
    void close() throws Exception;

    String getName();
    boolean exists();
    void delete() throws Exception;
}

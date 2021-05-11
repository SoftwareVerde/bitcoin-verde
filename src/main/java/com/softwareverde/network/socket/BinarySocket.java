package com.softwareverde.network.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.util.ByteUtil;

public class BinarySocket extends Socket {
    public static Integer DEFAULT_BUFFER_PAGE_BYTE_COUNT = 1460; // MTU - IP - TCP = 1500 - 20 - 20 = 1460
    public static Integer DEFAULT_MAX_BUFFER_BYTE_COUNT = (int) (128L * ByteUtil.Unit.Binary.MEBIBYTES);

    protected final BinaryPacketFormat _binaryPacketFormat;

    public BinarySocket(final java.net.Socket socket, final BinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool) {
        this(socket, binaryPacketFormat, threadPool, DEFAULT_BUFFER_PAGE_BYTE_COUNT, DEFAULT_MAX_BUFFER_BYTE_COUNT);
    }

    public BinarySocket(final java.net.Socket socket, final BinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool, final Integer bufferPageByteCount, final Integer maxBufferByteCount) {
        super(
            socket,
            new BinarySocketReadThread(bufferPageByteCount, maxBufferByteCount, binaryPacketFormat),
            new BinarySocketWriteThread(bufferPageByteCount, maxBufferByteCount),
            threadPool
        );
        _binaryPacketFormat = binaryPacketFormat;
    }

    public BinaryPacketFormat getBinaryPacketFormat() {
        return _binaryPacketFormat;
    }

}

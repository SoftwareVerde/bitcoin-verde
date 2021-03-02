package com.softwareverde.network.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.util.ByteUtil;

public class BinarySocket extends Socket {
    public static Integer DEFAULT_BUFFER_PAGE_BYTE_COUNT = (1024 * 2);
    public static Integer DEFAULT_MAX_BUFFER_BYTE_COUNT = (int) (128L * ByteUtil.Unit.Binary.MEBIBYTES);

    protected final BinaryPacketFormat _binaryPacketFormat;

    public BinarySocket(final java.net.Socket socket, final BinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool) {
        super(socket, new BinarySocketReadThread(DEFAULT_BUFFER_PAGE_BYTE_COUNT, DEFAULT_MAX_BUFFER_BYTE_COUNT, binaryPacketFormat), threadPool);
        _binaryPacketFormat = binaryPacketFormat;
    }

    public void setBufferPageByteCount(final Integer bufferSize) {
        ((BinarySocketReadThread) _readThread).setBufferPageByteCount(bufferSize);
    }

    public void setBufferMaxByteCount(final Integer totalMaxBufferSize) {
        ((BinarySocketReadThread) _readThread).setBufferMaxByteCount(totalMaxBufferSize);
    }

    public Integer getBufferPageByteCount() {
        final PacketBuffer packetBuffer = ((BinarySocketReadThread) _readThread).getProtocolMessageBuffer();
        return packetBuffer.getPageByteCount();
    }

    public Integer getBufferMaxByteCount() {
        final PacketBuffer packetBuffer = ((BinarySocketReadThread) _readThread).getProtocolMessageBuffer();
        return packetBuffer.getMaxByteCount();
    }

    public BinaryPacketFormat getBinaryPacketFormat() {
        return _binaryPacketFormat;
    }

}

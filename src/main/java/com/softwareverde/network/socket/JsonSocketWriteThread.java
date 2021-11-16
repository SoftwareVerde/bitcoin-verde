package com.softwareverde.network.socket;

public class JsonSocketWriteThread extends BinarySocketWriteThread {
    public JsonSocketWriteThread() {
        this(BinarySocket.DEFAULT_BUFFER_PAGE_BYTE_COUNT, BinarySocket.DEFAULT_MAX_BUFFER_BYTE_COUNT);
    }

    public JsonSocketWriteThread(final Integer bufferByteCount, final Integer maxQueuedMessageBufferByteCount) {
        super(bufferByteCount, maxQueuedMessageBufferByteCount);
    }
}

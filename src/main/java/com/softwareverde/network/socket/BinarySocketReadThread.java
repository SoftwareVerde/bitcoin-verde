package com.softwareverde.network.socket;

import com.softwareverde.logging.Logger;
import com.softwareverde.logging.LoggerInstance;
import com.softwareverde.network.p2p.message.ProtocolMessage;

import java.io.IOException;
import java.io.InputStream;

public class BinarySocketReadThread extends Thread implements Socket.ReadThread {
    private static final LoggerInstance LOG = Logger.getInstance(BinarySocketReadThread.class);

    private final PacketBuffer _protocolMessageBuffer;
    private InputStream _rawInputStream;
    private Callback _callback;

    private Long _totalBytesReceived = 0L;

    public BinarySocketReadThread(final Integer bufferPageSize, final Integer maxByteCount, final BinaryPacketFormat binaryPacketFormat) {
        this.setName("Binary Socket - Read Thread - " + this.getId());

        _protocolMessageBuffer = new PacketBuffer(binaryPacketFormat);
        _protocolMessageBuffer.setPageByteCount(bufferPageSize);
        _protocolMessageBuffer.setMaxByteCount(maxByteCount);
    }

    @Override
    public void run() {
        final Thread thread = Thread.currentThread();
        try {
            while (! thread.isInterrupted()) {
                final byte[] buffer = _protocolMessageBuffer.getRecycledBuffer();
                final int bytesRead = _rawInputStream.read(buffer);

                if (bytesRead < 0) {
                    throw new IOException("IO: Remote socket closed the connection.");
                }

                _totalBytesReceived += bytesRead;

                _protocolMessageBuffer.appendBytes(buffer, bytesRead);
                _protocolMessageBuffer.evictCorruptedPackets();

                if (LOG.isDebugEnabled()) {
                    final int byteCount = _protocolMessageBuffer.getByteCount();
                    final int bufferPageCount = _protocolMessageBuffer.getPageCount();
                    final int bufferPageByteCount = _protocolMessageBuffer.getPageByteCount();
                    final float utilizationPercent = ((int) (byteCount / (bufferPageCount * ((float) bufferPageByteCount)) * 100));
                    LOG.debug("[Received " + bytesRead + " bytes from socket.] (Bytes In Buffer: " + byteCount + ") (Buffer Count: " + bufferPageCount + ") (" + utilizationPercent + "%)");
                }

                while (_protocolMessageBuffer.hasMessage()) {
                    final ProtocolMessage message = _protocolMessageBuffer.popMessage();
                    _protocolMessageBuffer.evictCorruptedPackets();

                    if (_callback != null) {
                        if (message != null) {
                            _callback.onNewMessage(message);
                        }
                    }
                }
            }
        }
        catch (final Exception exception) {
            LOG.debug(exception);
        }
        finally {
            final Callback callback = _callback;
            if (callback != null) {
                callback.onExit();
            }
        }
    }

    @Override
    public synchronized void setInputStream(final InputStream inputStream) {
        final InputStream rawInputStream = _rawInputStream;
        if (rawInputStream != null) {
            try {
                rawInputStream.close();
            }
            catch (final Exception exception) { }
        }

        _rawInputStream = inputStream;
    }

    @Override
    public void setCallback(final Callback callback) {
        _callback = callback;
    }

    public void setBufferPageByteCount(final Integer pageSize) {
        _protocolMessageBuffer.setPageByteCount(pageSize);
    }

    public void setBufferMaxByteCount(final Integer bufferSize) {
        _protocolMessageBuffer.setMaxByteCount(bufferSize);
    }

    public PacketBuffer getProtocolMessageBuffer() {
        return _protocolMessageBuffer;
    }

    @Override
    public Long getTotalBytesReceived() {
        return _totalBytesReceived;
    }

    @Override
    public synchronized void close() {
        this.interrupt();

        final InputStream rawInputStream = _rawInputStream;
        if (rawInputStream != null) {
            try {
                rawInputStream.close();
            }
            catch (final Exception exception) { }
        }
        _rawInputStream = null;
    }
}
package com.softwareverde.network.socket;

import com.softwareverde.logging.Logger;
import com.softwareverde.logging.LoggerInstance;
import com.softwareverde.network.p2p.message.ProtocolMessage;

import java.io.IOException;
import java.io.InputStream;

public class BinarySocketReadThread extends Thread implements Socket.ReadThread {
    protected static final LoggerInstance LOG = Logger.getInstance(BinarySocketReadThread.class);

    protected final PacketBuffer _packetBuffer;
    protected InputStream _inputStream;
    protected Callback _callback;

    protected Long _totalBytesReceived = 0L;
    protected String _socketName;

    public BinarySocketReadThread(final Integer bufferPageSize, final Integer maxByteCount, final BinaryPacketFormat binaryPacketFormat) {
        this.setName("Binary Socket - Read Thread");

        _packetBuffer = new PacketBuffer(binaryPacketFormat);
        _packetBuffer.setPageByteCount(bufferPageSize);
        _packetBuffer.setMaxByteCount(maxByteCount);
    }

    @Override
    public void setSocketName(final String socketName) {
        this.setName("Binary Socket - Read Thread - " + socketName);
        _socketName = socketName;
    }

    @Override
    public void run() {
        final Thread thread = Thread.currentThread();
        try {
            final InputStream inputStream = _inputStream;

            while (! thread.isInterrupted()) {
                final byte[] buffer = _packetBuffer.getRecycledBuffer();
                final int bytesRead = inputStream.read(buffer);

                if (bytesRead < 0) {
                    throw new IOException("IO: Remote socket closed the connection.");
                }

                _totalBytesReceived += bytesRead;

                _packetBuffer.appendBytes(buffer, bytesRead);
                _packetBuffer.evictCorruptedPackets();

                if (LOG.isTraceEnabled()) {
                    final int byteCount = _packetBuffer.getByteCount();
                    final int bufferPageCount = _packetBuffer.getPageCount();
                    final int bufferPageByteCount = _packetBuffer.getPageByteCount();
                    final float utilizationPercent = ((int) (byteCount / (bufferPageCount * ((float) bufferPageByteCount)) * 100));
                    LOG.trace("Received " + bytesRead + " bytes from socket " + _socketName + ". (Bytes In Buffer: " + byteCount + ") (Buffer Count: " + bufferPageCount + ") (" + utilizationPercent + "%)");
                }

                while (_packetBuffer.hasMessage()) {
                    final ProtocolMessage message = _packetBuffer.popMessage();
                    _packetBuffer.evictCorruptedPackets();

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
        final InputStream rawInputStream = _inputStream;
        if (rawInputStream != null) {
            try {
                rawInputStream.close();
            }
            catch (final Exception exception) { }
        }

        _inputStream = inputStream;
    }

    @Override
    public void setCallback(final Callback callback) {
        _callback = callback;
    }

    public void setBufferPageByteCount(final Integer pageSize) {
        _packetBuffer.setPageByteCount(pageSize);
    }

    public void setBufferMaxByteCount(final Integer bufferSize) {
        _packetBuffer.setMaxByteCount(bufferSize);
    }

    public Integer getBufferPageByteCount() {
        return _packetBuffer.getPageByteCount();
    }

    public Integer getBufferMaxByteCount() {
        return _packetBuffer.getMaxByteCount();
    }

    @Override
    public Long getTotalBytesReceived() {
        return _totalBytesReceived;
    }

    @Override
    public synchronized void close() {
        this.interrupt();

        final InputStream rawInputStream = _inputStream;
        if (rawInputStream != null) {
            try {
                rawInputStream.close();
            }
            catch (final Exception exception) { }
        }
        _inputStream = null;
    }
}
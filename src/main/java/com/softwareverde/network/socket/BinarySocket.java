package com.softwareverde.network.socket;

import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.logging.Logger;
import com.softwareverde.logging.LoggerInstance;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.util.ByteUtil;

import java.io.IOException;
import java.io.InputStream;

public class BinarySocket extends Socket {
    public static Integer DEFAULT_BUFFER_PAGE_BYTE_COUNT = (1024 * 2);
    public static Integer DEFAULT_MAX_BUFFER_BYTE_COUNT = (int) (128L * ByteUtil.Unit.Binary.MEBIBYTES);

    protected static class ReadThread extends Thread implements Socket.ReadThread {
        private static final LoggerInstance LOG = Logger.getInstance(ReadThread.class);

        private final PacketBuffer _protocolMessageBuffer;
        private InputStream _rawInputStream;
        private Callback _callback;

        public ReadThread(final Integer bufferPageSize, final Integer maxByteCount, final BinaryPacketFormat binaryPacketFormat) {
            this.setName("Bitcoin Socket - Read Thread - " + this.getId());

            _protocolMessageBuffer = new PacketBuffer(binaryPacketFormat);
            _protocolMessageBuffer.setPageByteCount(bufferPageSize);
            _protocolMessageBuffer.setMaxByteCount(maxByteCount);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final byte[] buffer = _protocolMessageBuffer.getRecycledBuffer();
                    final int bytesRead = _rawInputStream.read(buffer);

                    if (bytesRead < 0) {
                        throw new IOException("IO: Remote socket closed the connection.");
                    }

                    _protocolMessageBuffer.appendBytes(buffer, bytesRead);
                    _protocolMessageBuffer.evictCorruptedPackets();

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Received " + bytesRead + " bytes from socket.] (Bytes In Buffer: " + _protocolMessageBuffer.getByteCount() + ") (Buffer Count: " + _protocolMessageBuffer.getBufferCount() + ") (" + ((int) (_protocolMessageBuffer.getByteCount() / (_protocolMessageBuffer.getBufferCount() * _protocolMessageBuffer.getBufferSize().floatValue()) * 100)) + "%)");
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

                    if (this.isInterrupted()) { break; }
                }
                catch (final Exception exception) {
                    LOG.debug(exception);
                    break;
                }
            }

            if (_callback != null) {
                _callback.onExit();
            }
        }

        @Override
        public void setInputStream(final InputStream inputStream) {
            if (_rawInputStream != null) {
                try {
                    _rawInputStream.close();
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
    }

    protected final BinaryPacketFormat _binaryPacketFormat;

    public BinarySocket(final java.net.Socket socket, final BinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool) {
        super(socket, new ReadThread(DEFAULT_BUFFER_PAGE_BYTE_COUNT, DEFAULT_MAX_BUFFER_BYTE_COUNT, binaryPacketFormat), threadPool);
        _binaryPacketFormat = binaryPacketFormat;
    }

    public void setBufferPageByteCount(final Integer bufferSize) {
        ((ReadThread) _readThread).setBufferPageByteCount(bufferSize);
    }

    public void setBufferMaxByteCount(final Integer totalMaxBufferSize) {
        ((ReadThread) _readThread).setBufferMaxByteCount(totalMaxBufferSize);
    }

    public Integer getBufferPageByteCount() {
        final PacketBuffer packetBuffer = ((ReadThread) _readThread).getProtocolMessageBuffer();
        return packetBuffer.getPageByteCount();
    }

    public Integer getBufferMaxByteCount() {
        final PacketBuffer packetBuffer = ((ReadThread) _readThread).getProtocolMessageBuffer();
        return packetBuffer.getMaxByteCount();
    }

    public BinaryPacketFormat getBinaryPacketFormat() {
        return _binaryPacketFormat;
    }
}

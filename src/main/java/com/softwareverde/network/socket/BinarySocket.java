package com.softwareverde.network.socket;

import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;

import java.io.IOException;
import java.io.InputStream;

public class BinarySocket extends Socket {
    public static Integer DEFAULT_BUFFER_SIZE = 1024 * 2;

    protected static class ReadThread extends Thread implements Socket.ReadThread {
        private final PacketBuffer _protocolMessageBuffer;
        private InputStream _rawInputStream;
        private Callback _callback;

        public ReadThread(final Integer bufferSize, final BinaryPacketFormat binaryPacketFormat) {
            this.setName("Bitcoin Socket - Read Thread - " + this.getId());

            _protocolMessageBuffer = new PacketBuffer(binaryPacketFormat);
            _protocolMessageBuffer.setBufferSize(bufferSize);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final byte[] buffer = _protocolMessageBuffer.getRecycledBuffer();
                    final Integer bytesRead = _rawInputStream.read(buffer);

                    if (bytesRead < 0) {
                        throw new IOException("IO: Remote socket closed the connection.");
                    }

                    _protocolMessageBuffer.appendBytes(buffer, bytesRead);
                    if (LOGGING_ENABLED) {
                        Logger.log("IO: [Received "+ bytesRead + " bytes from socket.] (Bytes In Buffer: "+ _protocolMessageBuffer.getByteCount() +") (Buffer Count: "+ _protocolMessageBuffer.getBufferCount() +") ("+ ((int) (_protocolMessageBuffer.getByteCount() / (_protocolMessageBuffer.getBufferCount() * _protocolMessageBuffer.getBufferSize().floatValue()) * 100)) +"%)");
                    }

                    while (_protocolMessageBuffer.hasMessage()) {
                        final ProtocolMessage message = _protocolMessageBuffer.popMessage();

                        if (_callback != null) {
                            if (message != null) {
                                _callback.onNewMessage(message);
                            }
                        }
                    }

                    if (this.isInterrupted()) { break; }
                }
                catch (final Exception exception) {
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

        public void setBufferSize(final Integer bufferSize) {
            _protocolMessageBuffer.setBufferSize(bufferSize);
        }
    }

    protected final BinaryPacketFormat _binaryPacketFormat;
    protected Integer _bufferSize;

    public BinarySocket(final java.net.Socket socket, final BinaryPacketFormat binaryPacketFormat) {
        super(socket, new ReadThread(DEFAULT_BUFFER_SIZE, binaryPacketFormat));
        _binaryPacketFormat = binaryPacketFormat;
        _bufferSize = DEFAULT_BUFFER_SIZE;
    }

    public void setBufferSize(final Integer bufferSize) {
        _bufferSize = bufferSize;
        ((ReadThread) _readThread).setBufferSize(bufferSize);
    }

    public Integer getBufferSize() {
        return _bufferSize;
    }

    public BinaryPacketFormat getBinaryPacketFormat() {
        return _binaryPacketFormat;
    }
}

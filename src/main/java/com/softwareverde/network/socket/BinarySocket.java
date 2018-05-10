package com.softwareverde.network.socket;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedList;

public class BinarySocket {
    public static Boolean LOGGING_ENABLED = false;

    private static final Object _nextIdMutex = new Object();
    private static Long _nextId = 0L;

    protected class ReadThread extends Thread {
        private final PacketBuffer _protocolMessageBuffer;

        public ReadThread(final BinaryPacketFormat binaryPacketFormat) {
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
                        Logger.log("IO: [Received "+ bytesRead + " bytes from socket.] (Bytes In Buffer: "+ _protocolMessageBuffer.getByteCount() +") (Buffer Count: "+ _protocolMessageBuffer.getBufferCount() +") ("+ ((int) (_protocolMessageBuffer.getByteCount() / (_protocolMessageBuffer.getBufferCount() * bufferSize.floatValue()) * 100)) +"%)");
                    }

                    while (_protocolMessageBuffer.hasMessage()) {
                        final ProtocolMessage message = _protocolMessageBuffer.popMessage();

                        if (message != null) {
                            synchronized (_messages) {
                                if (LOGGING_ENABLED) {
                                    Logger.log("IO: Received " + message.getCommand() + " message.");
                                }

                                _messages.addLast(message);

                                _onMessageReceived(message);
                            }
                        }
                    }

                    if (this.isInterrupted()) { break; }
                }
                catch (final Exception exception) {
                    break;
                }
            }

            if (! _isClosed) {
                _closeSocket();
            }
        }
    }

    protected final Long _id;
    protected final Socket _socket;
    protected final LinkedList<ProtocolMessage> _messages = new LinkedList<ProtocolMessage>();
    protected Boolean _isClosed = false;

    protected Runnable _messageReceivedCallback;
    protected final Thread _readThread;

    protected final OutputStream _rawOutputStream;
    protected final InputStream _rawInputStream;

    public Integer bufferSize = 1024 * 2;

    /**
     * Internal callback that is executed when a message is received by the client.
     *  Is executed before any external callbacks are received.
     *  Intended for subclass extension.
     */
    protected void _onMessageReceived(final ProtocolMessage message) {
        if (_messageReceivedCallback != null) {
            (new Thread(_messageReceivedCallback)).start();
        }
    }

    /**
     * Internal callback that is executed when the connection is closed by either the client or server,
     *  or if the connection is terminated.
     *  Intended for subclass extension.
     */
    protected void _onSocketClosed() {
        // Nothing.
    }

    protected void _closeSocket() {
        if (LOGGING_ENABLED) {
            Logger.log("Closing socket. Thread Id: " + Thread.currentThread().getId() + " " + _socket.getRemoteSocketAddress());
        }

        final Boolean wasClosed = _isClosed;

        _isClosed = true;

        _readThread.interrupt();

        try {
            _rawInputStream.close();
        }
        catch (final Exception exception) { }

        try {
            _rawOutputStream.close();
        }
        catch (final Exception exception) { }

        try {
            _readThread.join();
        }
        catch (final Exception exception) { }

        try {
            _socket.close();
        }
        catch (final Exception exception) { }

        if (! wasClosed) {
            _onSocketClosed();
        }
    }

    public BinarySocket(final Socket socket, final BinaryPacketFormat binaryPacketFormat) {
        synchronized (_nextIdMutex) {
            _id = _nextId;
            _nextId += 1;
        }

        _socket = socket;

        InputStream inputStream = null;
        OutputStream outputStream = null;
        { // Initialize the input and output streams...
            try {
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
            }
            catch (final IOException exception) { }
        }
        _rawOutputStream = outputStream;
        _rawInputStream = inputStream;

        _readThread = new ReadThread(binaryPacketFormat);

        _readThread.start();
    }

    public void setMessageReceivedCallback(final Runnable callback) {
        _messageReceivedCallback = callback;
    }

    synchronized public void write(final ProtocolMessage outboundMessage) {
        final ByteArray bytes = outboundMessage.getBytes();

        try {
            _rawOutputStream.write(bytes.getBytes());
            _rawOutputStream.flush();
        }
        catch (final Exception e) {
            _closeSocket();
        }

        if (LOGGING_ENABLED) {
            Logger.log("IO: Sent " + outboundMessage.getCommand() + " message.");
        }
    }

    /**
     * Retrieves the oldest message from the inbound queue and returns it.
     *  Returns null if there are no pending messages.
     */
    public ProtocolMessage popMessage() {
        synchronized (_messages) {
            if (_messages.isEmpty()) { return null; }

            return _messages.removeFirst();
        }
    }

    /**
     * Ceases all reads, and closes the socket.
     *  Invoking any write functions after this call throws a runtime exception.
     */
    public void close() {
        _closeSocket();
    }

    /**
     * Returns false if this instance has had its close() function invoked or the socket is no longer connected.
     */
    public Boolean isConnected() {
        return (! (_isClosed || _socket.isClosed()));
    }

    @Override
    public int hashCode() {
        return (BinarySocket.class.getSimpleName().hashCode() + _id.hashCode());
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof BinarySocket)) { return false; }

        final BinarySocket socketConnectionObject = (BinarySocket) object;
        return _id.equals(socketConnectionObject._id);
    }
}

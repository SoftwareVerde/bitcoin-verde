package com.softwareverde.bitcoin.server.socket;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.io.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class BitcoinSocket {

    private static final Object _nextIdMutex = new Object();
    private static Long _nextId = 0L;

    private final Long _id;
    private final Socket _socket;
    private final List<ProtocolMessage> _messages = new ArrayList<ProtocolMessage>();
    private volatile Boolean _isClosed = false;

    private Runnable _messageReceivedCallback;
    private final Thread _readThread;

    private final OutputStream _rawOutputStream;
    private final InputStream _rawInputStream;

    public Integer bufferSize = 1024 * 2;

    private void _executeMessageReceivedCallback() {
        if (_messageReceivedCallback != null) {
            (new Thread(_messageReceivedCallback)).start();
        }
    }

    /**
     * Internal callback that is executed when a message is received by the client.
     *  Is executed before any external callbacks are received.
     *  Intended for subclass extension.
     */
    protected void _onMessageReceived(final ProtocolMessage message) {
        // Nothing.
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
        final Boolean wasClosed = _isClosed;
        _isClosed = true;

        if (! wasClosed) {
            _onSocketClosed();
        }
    }

    public BitcoinSocket(final Socket socket) {
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

        _readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final PacketBuffer protocolMessageBuffer = new PacketBuffer();

                protocolMessageBuffer.setBufferSize(bufferSize);

                while (! _isClosed) {
                    try {
                        final byte[] buffer = protocolMessageBuffer.getRecycledBuffer();
                        final Integer bytesRead = _rawInputStream.read(buffer);

                        if (bytesRead < 0) {
                            throw new IOException("IO: Remote socket closed the connection.");
                        }

                        protocolMessageBuffer.appendBytes(buffer, bytesRead);
                        Logger.log("IO: [Received "+ bytesRead + " bytes from socket.] (Bytes In Buffer: "+ protocolMessageBuffer.getByteCount() +") (Buffer Count: "+ protocolMessageBuffer.getBufferCount() +") ("+ ((int) (protocolMessageBuffer.getByteCount() / (protocolMessageBuffer.getBufferCount() * bufferSize.floatValue()) * 100)) +"%)");

                        while (protocolMessageBuffer.hasMessage()) {
                            final ProtocolMessage message = protocolMessageBuffer.popMessage();

                            if (message != null) {
                                synchronized (_messages) {
                                    Logger.log("IO: Received "+ message.getCommand() +" message.");

                                    _messages.add(message);

                                    _onMessageReceived(message);
                                    _executeMessageReceivedCallback();
                                }
                            }
                        }
                    }
                    catch (final IOException exception) {
                        _closeSocket();
                        break;
                    }
                }
            }
        });

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

        Logger.log("IO: Sent "+ outboundMessage.getCommand() +" message.");
    }

    /**
     * Retrieves the oldest message from the inbound queue and returns it.
     *  Returns null if there are no pending messages.
     */
    public ProtocolMessage popMessage() {
        synchronized (_messages) {
            if (_messages.isEmpty()) { return null; }

            return _messages.remove(0);
        }
    }

    /**
     * Ceases all reads, and closes the socket.
     *  Invoking any write functions after this call throws a runtime exception.
     */
    public void close() {
        _isClosed = true;

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
        catch (final InterruptedException e) { }

        try {
            _socket.close();
        }
        catch (final IOException e) { }

        _onSocketClosed();
    }

    /**
     * Returns false if this instance has had its close() function invoked or the socket is no longer connected.
     */
    public Boolean isConnected() {
        return (! (_isClosed || _socket.isClosed()));
    }

    @Override
    public int hashCode() {
        return (BitcoinSocket.class.getSimpleName().hashCode() + _id.hashCode());
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof BitcoinSocket)) { return false; }

        final BitcoinSocket socketConnectionObject = (BitcoinSocket) object;
        return _id.equals(socketConnectionObject._id);
    }
}

package com.softwareverde.bitcoin.server.socket;

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
    private OutputStream _out;
    private final List<byte[]> _messages = new ArrayList<byte[]>();
    private volatile Boolean _isClosed = false;

    private Runnable _messageReceivedCallback;
    private Thread _readThread;

    private InputStream _rawInputStream;

    public Integer bufferSize = 2048;

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
    protected void _onMessageReceived(final byte[] message) {
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

        try {
            _out = socket.getOutputStream();

            _rawInputStream = socket.getInputStream();
        }
        catch (final IOException e) { }

        _readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (! _isClosed) {
                    try {
                        final byte[] buffer = new byte[bufferSize];
                        final Integer bytesRead = _rawInputStream.read(buffer);
                        System.out.println("Read: "+ bytesRead);

                        if (bytesRead < 0) {
                            throw new IOException("Remote socket closed the connection.");
                        }

                        final byte[] message = new byte[bytesRead];
                        for (int i=0; i<bytesRead; ++i) {
                            message[i] = (byte) buffer[i];
                        }

                        synchronized (_messages) {
                            _messages.add(message);

                            _onMessageReceived(message);
                            _executeMessageReceivedCallback();
                        }
                    }
                    catch (final IOException exception) {
                        _isClosed = true;
                        _onSocketClosed();
                    }
                }
            }
        });

        _readThread.start();
    }

    public void setMessageReceivedCallback(final Runnable callback) {
        _messageReceivedCallback = callback;
    }

    synchronized public void write(final byte[] outboundMessage) {
        try {
            _out.write(outboundMessage);
            _out.flush();
        }
        catch (final Exception e) {
            _closeSocket();
        }
    }

    /**
     * Retrieves the oldest message from the inbound queue and returns it.
     *  Returns null if there are no pending messages.
     */
    public byte[] popMessage() {
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
        } catch (final Exception exception) { }

        try {
            _readThread.join();
        } catch (final InterruptedException e) { }

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

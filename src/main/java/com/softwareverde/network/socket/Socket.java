package com.softwareverde.network.socket;

import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.message.ProtocolMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class Socket {
    public static Boolean LOGGING_ENABLED = false;

    private static final Object _nextIdMutex = new Object();
    private static Long _nextId = 0L;

    protected interface ReadThread {
        interface Callback {
            void onNewMessage(ProtocolMessage protocolMessage);
            void onExit();
        }

        void setInputStream(InputStream inputStream);
        void setCallback(Callback callback);
        void interrupt();
        void join() throws InterruptedException;
        void join(long timeout) throws InterruptedException;
        void start();
    }

    protected final Long _id;
    protected final java.net.Socket _socket;
    protected final ConcurrentLinkedQueue<ProtocolMessage> _messages = new ConcurrentLinkedQueue<ProtocolMessage>();
    protected Boolean _isClosed = false;

    protected Runnable _messageReceivedCallback;
    protected Runnable _socketClosedCallback;
    protected Boolean _isListening = false;
    protected final ReadThread _readThread;

    protected final OutputStream _rawOutputStream;
    protected final InputStream _rawInputStream;

    protected final ThreadPool _threadPool;

    protected final Object _rawOutputStreamWriteMutex = new Object();

    protected String _getHost() {
        Logger.log("INFO: Performing reverse lookup.");
        final InetAddress inetAddress = _socket.getInetAddress();
        return (inetAddress != null ? inetAddress.getHostName() : null);
    }

    protected Integer _getPort() {
        return _socket.getPort();
    }

    /**
     * Internal callback that is executed when a message is received by the client.
     *  Is executed before any external callbacks are received.
     *  Intended for subclass extension.
     */
    protected void _onMessageReceived(final ProtocolMessage message) {
        final Runnable messageReceivedCallback = _messageReceivedCallback;
        if (messageReceivedCallback != null) {
            _threadPool.execute(messageReceivedCallback);
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
            _readThread.join(5000L);
        }
        catch (final Exception exception) { }

        try {
            _socket.close();
        }
        catch (final Exception exception) { }

        final Runnable onCloseCallback = _socketClosedCallback;
        _socketClosedCallback = null;

        if (onCloseCallback != null) {
            _threadPool.execute(onCloseCallback);
        }

        if (! wasClosed) {
            _onSocketClosed();
        }
    }

    protected Socket(final java.net.Socket socket, final ReadThread readThread, final ThreadPool threadPool) {
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

        _readThread = readThread;
        _readThread.setInputStream(_rawInputStream);
        _readThread.setCallback(new ReadThread.Callback() {
            @Override
            public void onNewMessage(final ProtocolMessage message) {
                _messages.offer(message);
                _onMessageReceived(message);
            }

            @Override
            public void onExit() {
                if (! _isClosed) {
                    _closeSocket();
                }
            }
        });

        _threadPool = threadPool;
    }

    public synchronized void beginListening() {
        if (_isListening) { return; }

        _isListening = true;
        _readThread.start();
    }

    public void setMessageReceivedCallback(final Runnable callback) {
        _messageReceivedCallback = callback;
    }

    public void setOnClosedCallback(final Runnable callback) {
        _socketClosedCallback = callback;
    }

    public void write(final ProtocolMessage outboundMessage) {
        final ByteArray bytes = outboundMessage.getBytes();

        try {
            synchronized (_rawOutputStreamWriteMutex) {
                _rawOutputStream.write(bytes.getBytes());
                _rawOutputStream.flush();
            }
        }
        catch (final Exception exception) {
            _closeSocket();
        }
    }

    /**
     * Retrieves the oldest message from the inbound queue and returns it.
     *  Returns null if there are no pending messages.
     */
    public ProtocolMessage popMessage() {
        return _messages.poll();
    }

    /**
     * Attempts to return the DNS lookup of the connection or null if the lookup fails.
     */
    public String getHost() {
        return _getHost();
    }

    public Ip getIp() {
        return Ip.fromSocket(_socket);
    }

    public Integer getPort() {
        return _getPort();
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
        return ( (! _isClosed) && (! _socket.isClosed()) );
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

    @Override
    public String toString() {
        return (Ip.fromSocket(_socket) + ":" + _getPort());
    }
}

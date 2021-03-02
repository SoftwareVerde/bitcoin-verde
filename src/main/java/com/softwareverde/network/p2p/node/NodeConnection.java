package com.softwareverde.network.p2p.node;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.socket.BinaryPacketFormat;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.Util;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NodeConnection {
    public interface MessageReceivedCallback {
        void onMessageReceived(ProtocolMessage message);
    }

    protected static final Integer MAX_CONNECTION_ATTEMPTS = 10; // Sanity check for connection attempts...

    protected static Boolean _socketIsConnected(final BinarySocket binarySocket) {
        return ( (binarySocket != null) && (binarySocket.isConnected()) );
    }

    protected class ConnectionThread extends Thread {
        @Override
        public void run() {
            if (_socketIsConnected(_binarySocket)) {
                _onSocketConnected(); // Necessary for NodeConnection(BinarySocket) constructor...
                return;
            }

            if (_socketUsedToBeConnected) {

                final Runnable onDisconnectCallback = _onDisconnectCallback;
                if (onDisconnectCallback != null) {
                    _threadPool.execute(onDisconnectCallback);
                }
                _socketUsedToBeConnected = false;

                Logger.debug("Connection lost. " + _toString());
            }

            Socket socket = null;

            int attemptCount = 0;
            while (true) {
                if (attemptCount >= MAX_CONNECTION_ATTEMPTS) {
                    Logger.info("Connection could not be established. Max attempts reached. " + _toString());
                    break;
                }

                if (_socketIsConnected(_binarySocket)) { break; }

                try {
                    attemptCount += 1;
                    socket = new Socket(_host, _port);
                    if (socket.isConnected()) { break; }
                }
                catch (final UnknownHostException exception) {
                    Logger.info("Connection could not be established. Unknown host: " + _toString());
                    break;
                }
                catch (final IOException exception) { }

                if ( (socket == null) || (! socket.isConnected()) ) {
                    final long timeoutMs = 3000L;
                    Logger.debug("Connection failed. Retrying in " + timeoutMs + "ms... (" + _toString() + ")");
                    try { Thread.sleep(timeoutMs); } catch (final Exception exception) { break; }
                }
            }

            if ( (socket != null) && (socket.isConnected()) ) {
                _binarySocket = new BinarySocket(socket, _binaryPacketFormat, _threadPool);
                _binarySocket.setOnClosedCallback(new Runnable() {
                    @Override
                    public void run() {
                        _disconnect();
                    }
                });
                _onSocketConnected();
            }
            else {
                final Runnable onConnectFailureCallback = _onConnectFailureCallback;
                if (onConnectFailureCallback != null) {
                    _threadPool.execute(onConnectFailureCallback);
                }
            }
        }
    }

    protected final String _host;
    protected final Integer _port;
    protected final BinaryPacketFormat _binaryPacketFormat;

    protected final ConcurrentLinkedQueue<ProtocolMessage> _outboundMessageQueue = new ConcurrentLinkedQueue<ProtocolMessage>();

    protected final Object _connectionThreadMutex = new Object();
    protected BinarySocket _binarySocket;
    protected Thread _connectionThread;
    protected MessageReceivedCallback _messageReceivedCallback;

    protected Boolean _socketUsedToBeConnected = false;

    protected Long _connectionCount = 0L;
    protected Runnable _onDisconnectCallback;
    protected Runnable _onReconnectCallback;
    protected Runnable _onConnectCallback;
    protected Runnable _onConnectFailureCallback;

    protected final ThreadPool _threadPool;

    protected String _toString() {
        String hostString = _host;
        {
            final BinarySocket binarySocket = _binarySocket;
            if (binarySocket != null) {
                final Ip ip = binarySocket.getIp();
                if (ip != null) {
                    hostString = ip.toString();
                }
            }
        }

        return (hostString + ":" + _port);
    }

    protected void _shutdownConnectionThread() {
        final Thread connectionThread = _connectionThread;
        if (connectionThread == null) { return; }

        _connectionThread = null;
        connectionThread.interrupt();
    }

    protected void _onSocketConnected() {
        _binarySocket.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                final BinarySocket binarySocket = _binarySocket;
                if (binarySocket == null) { return; } // Can most likely happen when disconnected before the callback was executed from the threadPool...

                final ProtocolMessage message = binarySocket.popMessage();

                // if (message instanceof BitcoinProtocolMessage) {
                //     Logger.trace("Received: " + ((BitcoinProtocolMessage) message).getCommand() + " " + _toString());
                // }

                final MessageReceivedCallback messageReceivedCallback = _messageReceivedCallback;
                if (messageReceivedCallback != null) {
                    messageReceivedCallback.onMessageReceived(message);
                }
            }
        });
        _binarySocket.beginListening();

        final boolean isFirstConnection = (_connectionCount == 0);
        if (isFirstConnection) {
            Logger.debug("Connection established. " + _toString());

            _processOutboundMessageQueue();

            final Runnable onConnectCallback = _onConnectCallback;
            if (onConnectCallback != null) {
                _threadPool.execute(onConnectCallback);
            }
        }
        else {
            Logger.debug("Connection regained. " + _toString());
            _processOutboundMessageQueue();

            final Runnable onReconnectCallback = _onReconnectCallback;
            if (onReconnectCallback != null) {
                _threadPool.execute(onReconnectCallback);
            }
        }

        _socketUsedToBeConnected = true;
        _connectionCount += 1;
    }

    /**
     * _onMessagesProcessed is executed whenever the outboundMessageQueue has been processed.
     *  This method is intended to serve as a callback for subclasses.
     *  Invocation of this callback does not necessarily guarantee that the queue is empty, although it is likely.
     */
    protected void _onMessagesProcessed() {
        // Nothing.
    }

    protected void _disconnect() {
        Logger.debug("Disconnecting " + _toString());

        final Runnable onDisconnectCallback = _onDisconnectCallback;

        _messageReceivedCallback = null;
        _onDisconnectCallback = null;
        _onReconnectCallback = null;
        _onConnectCallback = null;
        _onConnectFailureCallback = null;

        synchronized (_connectionThreadMutex) {
            if (_connectionThread != null) {
                _shutdownConnectionThread();
            }
        }

        final BinarySocket binarySocket = _binarySocket;
        _binarySocket = null;
        if (binarySocket != null) {
            binarySocket.setMessageReceivedCallback(null);
            binarySocket.setOnClosedCallback(null);
            binarySocket.close();
        }

        if (onDisconnectCallback != null) {
            final Thread thread = new Thread(onDisconnectCallback);
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.error("Uncaught exception in thread.", exception);
                }
            });
            thread.start();
        }
    }

    protected void _processOutboundMessageQueue() {
        ProtocolMessage message;
        while ((message = _outboundMessageQueue.poll()) != null) {
            final BinarySocket binarySocket = _binarySocket;
            if (! _socketIsConnected(binarySocket)) {
                _outboundMessageQueue.offer(message); // Return the item to the queue (not ideal, as this queues it to the back, but it's acceptable)...
                return;
            }

            // if (message instanceof BitcoinProtocolMessage) {
            //     Logger.debug("Wrote: " + (((BitcoinProtocolMessage) message).getCommand()) + " " + _toString());
            // }

            binarySocket.write(message);
        }

        _onMessagesProcessed();
    }

    protected void _writeOrQueueMessage(final ProtocolMessage message) {
        final boolean messageWasQueued;

        final BinarySocket binarySocket = _binarySocket;
        if (_socketIsConnected(binarySocket)) {
            binarySocket.write(message);
            messageWasQueued = false;
        }
        else {
            _outboundMessageQueue.offer(message);
            messageWasQueued = true;
        }

        // if (message instanceof BitcoinProtocolMessage) {
        //     Logger.debug((messageWasQueued ? "Queued" : "Wrote") + ": " + (((BitcoinProtocolMessage) message).getCommand()) + " " + _toString());
        // }

        if (! messageWasQueued) {
            _onMessagesProcessed();
        }
    }

    public NodeConnection(final String host, final Integer port, final BinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool) {
        _host = host;
        _port = port;

        _binaryPacketFormat = binaryPacketFormat;
        _threadPool = threadPool;
    }

    /**
     * Creates a NodeConnection with an already-established BinarySocket.
     *  NodeConnection::connect should still be invoked in order to initiate the ::_onSocketConnected procedure.
     */
    public NodeConnection(final BinarySocket binarySocket, final ThreadPool threadPool) {
        final Ip ip = binarySocket.getIp();
        _host = (ip != null ? ip.toString() : binarySocket.getHost());
        _port = binarySocket.getPort();
        _binarySocket = binarySocket;
        _binaryPacketFormat = binarySocket.getBinaryPacketFormat();
        _threadPool = threadPool;

        _binarySocket.setOnClosedCallback(new Runnable() {
            @Override
            public void run() {
                _disconnect();
            }
        });
    }

    /**
     * Starts the connection process.
     *  NodeConnection::connect is safe to call multiple times.
     */
    public void connect() {
        synchronized (_connectionThreadMutex) {
            { // Shutdown the existing connection thread, if it exists...
                final Thread connectionThread = _connectionThread;
                if (connectionThread != null) {
                    if (! connectionThread.isAlive()) {
                        _shutdownConnectionThread();
                    }
                }
            }

            if (_connectionThread == null) {
                final Thread connectionThread = new ConnectionThread();
                _connectionThread = connectionThread;
                connectionThread.start();
            }
        }
    }

    public void cancelConnecting() {
        synchronized (_connectionThreadMutex) {
            if (_connectionThread != null) {
                _shutdownConnectionThread();
            }
        }
    }

    public void setOnDisconnectCallback(final Runnable callback) {
        _onDisconnectCallback = callback;
    }

    public void setOnReconnectCallback(final Runnable callback) {
        _onReconnectCallback = callback;
    }

    public void setOnConnectCallback(final Runnable callback) {
        _onConnectCallback = callback;
    }

    public void setOnConnectFailureCallback(final Runnable callback) {
        _onConnectFailureCallback = callback;
    }

    public Boolean isConnected() {
        return _socketIsConnected(_binarySocket);
    }

    public void disconnect() {
        _disconnect();
    }

    public void queueMessage(final ProtocolMessage message) {
        // NOTE: Queuing the message into the ThreadPool can cause significant memory usage for large message if the message
        //  remains in the queue for its duration.
        //  This could be mitigated by pushing the write to the front of the queue...
        _writeOrQueueMessage(message);

        // _threadPool.execute(new Runnable() {
        //     @Override
        //     public void run() {
        //         _writeOrQueueMessage(message);
        //     }
        // });
    }

    public void setMessageReceivedCallback(final MessageReceivedCallback messageReceivedCallback) {
        _messageReceivedCallback = messageReceivedCallback;
    }

    /**
     * Attempts to return the hostname via a hostname lookup or the provided host if not found.
     */
    public String getHost() {
        final BinarySocket binarySocket = _binarySocket;
        if (binarySocket == null) { return _host; }

        return Util.coalesce(binarySocket.getHost(), _host);
    }

    public Integer getPort() { return _port; }

    /**
     * Returns the Ip if connected, or attempts to look up the Ip from the provided host.  Returns null if the host is unknown.
     */
    public Ip getIp() {
        final BinarySocket binarySocket = _binarySocket;
        if (binarySocket != null) {
            return binarySocket.getIp();
        }

        return Ip.fromHostName(_host);
    }

    public BinaryPacketFormat getBinaryPacketFormat() {
        return _binaryPacketFormat;
    }

    public Long getTotalBytesReceivedCount() {
        final BinarySocket binarySocket = _binarySocket;
        if (binarySocket == null) { return 0L; }

        return binarySocket.getTotalBytesReceivedCount();
    }

    public Long getTotalBytesSentCount() {
        final BinarySocket binarySocket = _binarySocket;
        if (binarySocket == null) { return 0L; }

        return binarySocket.getTotalBytesSentCount();
    }

    @Override
    public String toString() {
        return _toString();
    }
}
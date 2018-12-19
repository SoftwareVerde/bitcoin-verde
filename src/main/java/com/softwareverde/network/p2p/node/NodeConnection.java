package com.softwareverde.network.p2p.node;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.socket.BinaryPacketFormat;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.StringUtil;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NodeConnection {
    public static Boolean LOGGING_ENABLED = true;

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

                if (LOGGING_ENABLED) {
                    Logger.log("IO: NodeConnection: Connection lost. " + _toString());
                }
            }

            Socket socket = null;

            int attemptCount = 0;
            while (true) {
                if (attemptCount >= MAX_CONNECTION_ATTEMPTS) {
                    if (LOGGING_ENABLED) {
                        Logger.log("IO: NodeConnection: Connection could not be established. Max attempts reached. " + _toString());
                    }
                    break;
                }

                if (_socketIsConnected(_binarySocket)) { break; }

                try {
                    attemptCount += 1;
                    socket = new Socket(_host, _port);
                    if (socket.isConnected()) { break; }
                }
                catch (final UnknownHostException exception) {
                    if (LOGGING_ENABLED) {
                        Logger.log("IO: NodeConnection: Connection could not be established. Unknown host: " + _toString());
                    }
                    break;
                }
                catch (final IOException e) { }

                if ( (socket == null) || (! socket.isConnected()) ) {
                    if (LOGGING_ENABLED) {
                        Logger.log("IO: NodeConnection: Connection failed. Retrying in 3000ms... (" + _toString() + ")");
                    }
                    try { Thread.sleep(3000); } catch (final Exception exception) { break; }
                }
            }

            if ( (socket != null) && (socket.isConnected()) ) {
                {
                    final SocketAddress socketAddress = socket.getRemoteSocketAddress();
                    final String socketIpString = socketAddress.toString();
                    final java.util.List<String> urlParts = StringUtil.pregMatch("^([^/]*)/([0-9:.]+):([0-9]+)$", socketIpString); // Example: btc.softwareverde.com/0.0.0.0:8333
                    // final String domainName = urlParts.get(0);
                    _remoteIp = urlParts.get(1); // Ip Address
                    // final String portString = urlParts.get(2);
                }

                _binarySocket = new BinarySocket(socket, _binaryPacketFormat, _threadPool);
                _onSocketConnected();
            }
            else {
                if (_onConnectFailureCallback != null) {
                    _onConnectFailureCallback.run();
                }
            }
        }
    }

    protected final String _host;
    protected final Integer _port;
    protected String _remoteIp;
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
        return (_host +":" + _port);
    }

    protected void _shutdownConnectionThread() {
        _connectionThread.interrupt();
        _connectionThread = null;
    }

    protected void _onSocketConnected() {
        _binarySocket.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                final ProtocolMessage protocolMessage = _binarySocket.popMessage();

                if (LOGGING_ENABLED) {
                    if (protocolMessage instanceof BitcoinProtocolMessage) {
                        Logger.log("Received: " + ((BitcoinProtocolMessage) protocolMessage).getCommand() + " " + _toString());
                    }
                }

                if (_messageReceivedCallback != null) {
                    _messageReceivedCallback.onMessageReceived(protocolMessage);
                }
            }
        });
        _binarySocket.beginListening();

        final Boolean isFirstConnection = (_connectionCount == 0);
        if (isFirstConnection) {
            if (LOGGING_ENABLED) {
                Logger.log("IO: NodeConnection: Connection established. " + _toString());
            }

            _processOutboundMessageQueue();

            final Runnable onConnectCallback = _onConnectCallback;
            if (onConnectCallback != null) {
                _threadPool.execute(onConnectCallback);
            }
        }
        else {
            if (LOGGING_ENABLED) {
                Logger.log("IO: NodeConnection: Connection regained. " + _toString());
            }
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

    protected void _processOutboundMessageQueue() {
        ProtocolMessage message;
        while ((message = _outboundMessageQueue.poll()) != null) {
            final BinarySocket binarySocket = _binarySocket;
            if (! _socketIsConnected(binarySocket)) {
                _outboundMessageQueue.offer(message); // Return the item to the queue (not ideal, as this queues it to the back, but it's acceptable)...
                return;
            }

            binarySocket.write(message);
        }

        _onMessagesProcessed();
    }

    protected void _writeOrQueueMessage(final ProtocolMessage message) {
        final Boolean messageWasQueued;

        final BinarySocket binarySocket = _binarySocket;
        if (_socketIsConnected(binarySocket)) {
            binarySocket.write(message);
            messageWasQueued = false;
        }
        else {
            _outboundMessageQueue.offer(message);
            messageWasQueued = true;
        }

        if (LOGGING_ENABLED) {
            if (message instanceof BitcoinProtocolMessage) {
                Logger.log((messageWasQueued ? "Queued" : "Wrote") + ": " + (((BitcoinProtocolMessage) message).getCommand()) + " " + _toString());
            }
        }

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
        _host = binarySocket.getHost();
        _port = binarySocket.getPort();
        _binarySocket = binarySocket;
        _binaryPacketFormat = binarySocket.getBinaryPacketFormat();
        _threadPool = threadPool;
    }

    public void connect() {
        synchronized (_connectionThreadMutex) {
            if (_connectionThread != null) {
                if (! _connectionThread.isAlive()) {
                    _shutdownConnectionThread();
                }
            }

            if (_connectionThread == null) {
                _connectionThread = new ConnectionThread();
                _connectionThread.start();
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
        synchronized (_connectionThreadMutex) {
            if (_connectionThread != null) {
                _shutdownConnectionThread();
            }
        }

        final BinarySocket binarySocket = _binarySocket;
        if (binarySocket != null) {
            binarySocket.close();
        }
    }

    public void queueMessage(final ProtocolMessage message) {
        if (message instanceof BitcoinProtocolMessage) {
            if (LOGGING_ENABLED) {
                Logger.log("Queuing: " + (((BitcoinProtocolMessage) message).getCommand()) + " " + _toString());
            }
        }

        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                _writeOrQueueMessage(message);
            }
        });
    }

    public void setMessageReceivedCallback(final MessageReceivedCallback messageReceivedCallback) {
        _messageReceivedCallback = messageReceivedCallback;
    }

    public String getRemoteIp() {
        return _remoteIp;
    }

    public String getHost() { return _host; }

    public Integer getPort() { return _port; }

    @Override
    public String toString() {
        return _toString();
    }
}
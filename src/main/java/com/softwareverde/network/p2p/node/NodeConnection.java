package com.softwareverde.network.p2p.node;

import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.socket.BinaryPacketFormat;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.StringUtil;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

public class NodeConnection {
    public interface MessageReceivedCallback {
        void onMessageReceived(ProtocolMessage message);
    }

    protected static final Integer MAX_CONNECTION_ATTEMPTS = 10; // Sanity check for connection attempts...

    protected class ConnectionThread extends Thread {
        @Override
        public void run() {
            if (_socketIsConnected()) { return; }

            if (_socketUsedToBeConnected) {
                if (_onDisconnectCallback != null) {
                    (new Thread(_onDisconnectCallback)).start();
                }
                _socketUsedToBeConnected = false;
                Logger.log("IO: NodeConnection: Connection lost.");
            }

            Socket socket = null;

            int attemptCount = 0;
            while (true) {
                if (attemptCount >= MAX_CONNECTION_ATTEMPTS) {
                    Logger.log("IO: NodeConnection: Connection could not be established. Max attempts reached.");
                    break;
                }

                if (_socketIsConnected()) { break; }

                try {
                    attemptCount += 1;
                    socket = new Socket(_host, _port);
                    if (socket.isConnected()) { break; }
                }
                catch (final UnknownHostException exception) {
                    Logger.log("IO: NodeConnection: Connection could not be established. Unknown host: " + _host + ":" + _port);
                    break;
                }
                catch (final IOException e) { }

                if ( (socket == null) || (! socket.isConnected()) ) {
                    Logger.log("IO: NodeConnection: Connection failed. Retrying in 3000ms... (" + (_host + ":" + _port) + ")");
                    try { Thread.sleep(3000); } catch (final Exception exception) { break; }
                }
            }

            if ( (socket != null) && (socket.isConnected()) ) {
                {
                    final SocketAddress socketAddress = socket.getRemoteSocketAddress();
                    final String socketIpString = socketAddress.toString();
                    final List<String> urlParts = StringUtil.pregMatch("^([^/]*)/([0-9:.]+):([0-9]+)$", socketIpString); // Example: btc.softwareverde.com/0.0.0.0:8333
                    // final String domainName = urlParts.get(0);
                    _remoteIp = urlParts.get(1); // Ip Address
                    // final String portString = urlParts.get(2);
                }

                _binarySocket = new BinarySocket(socket, _binaryPacketFormat);
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

    protected final LinkedList<ProtocolMessage> _outboundMessageQueue = new LinkedList<ProtocolMessage>();

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

    protected Boolean _socketIsConnected() {
        return ( (_binarySocket != null) && (_binarySocket.isConnected()) );
    }

    protected void _shutdownConnectionThread() {
        _connectionThread.interrupt();
        _connectionThread = null;
    }

    protected void _onSocketConnected() {
        _binarySocket.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                if (_messageReceivedCallback != null) {
                    _messageReceivedCallback.onMessageReceived(_binarySocket.popMessage());
                }
            }
        });
        _binarySocket.beginListening();

        final Boolean isFirstConnection = (_connectionCount == 0);
        if (isFirstConnection) {
            Logger.log("IO: NodeConnection: Connection established.");

            _processOutboundMessageQueue();

            if (_onConnectCallback != null) {
                (new Thread(_onConnectCallback)).start();
            }
        }
        else {
            Logger.log("IO: NodeConnection: Connection regained.");
            _processOutboundMessageQueue();

            if (_onReconnectCallback != null) {
                (new Thread(_onReconnectCallback)).start();
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
        synchronized (_outboundMessageQueue) {
            while (! _outboundMessageQueue.isEmpty()) {
                if ((_binarySocket == null) || (! _binarySocket.isConnected())) { return; }

                final ProtocolMessage message = _outboundMessageQueue.removeFirst();
                _binarySocket.write(message);
            }
        }

        _onMessagesProcessed();
    }

    protected void _writeOrQueueMessage(final ProtocolMessage message) {
        final Boolean messageWasQueued;

        synchronized (_outboundMessageQueue) {
            if (_socketIsConnected()) {
                _binarySocket.write(message);
                messageWasQueued = false;
            }
            else {
                _outboundMessageQueue.addLast(message);
                messageWasQueued = true;
            }
        }

        if (! messageWasQueued) {
            _onMessagesProcessed();
        }
    }


    public NodeConnection(final String host, final Integer port, final BinaryPacketFormat binaryPacketFormat) {
        _host = host;
        _port = port;

        _binaryPacketFormat = binaryPacketFormat;
    }

    public NodeConnection(final BinarySocket binarySocket) {
        _host = binarySocket.getHost();
        _port = binarySocket.getPort();
        _binarySocket = binarySocket;
        _binaryPacketFormat = binarySocket.getBinaryPacketFormat();

        _onSocketConnected();
    }

    public void startConnectionThread() {
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

    public void stopConnectionThread() {
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
        return _socketIsConnected();
    }

    public void disconnect() {
        synchronized (_connectionThreadMutex) {
            if (_connectionThread != null) {
                _shutdownConnectionThread();
            }
        }

        if (_binarySocket != null) {
            _binarySocket.close();
        }
    }

    public void queueMessage(final ProtocolMessage message) {
        (new Thread(new Runnable() {
            @Override
            public void run() {
                _writeOrQueueMessage(message);
            }
        })).start();
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
        return (_host +":" + _port);
    }
}
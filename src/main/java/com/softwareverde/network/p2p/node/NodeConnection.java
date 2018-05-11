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

    private static final Integer MAX_CONNECTION_ATTEMPTS = 1024; // Sanity check for connection attempts...

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
                    Logger.log("IO: NodeConnection: Connection failed. Retrying in 1000ms...");
                    try { Thread.sleep(1000); } catch (final Exception exception) { break; }
                }
            }

            if ( (socket != null) && (socket.isConnected()) ) {
                final SocketAddress socketAddress = socket.getRemoteSocketAddress();
                {
                    final String socketIpString = socketAddress.toString();
                    final List<String> urlParts = StringUtil.pregMatch("^([^/]*)/([0-9:.]+):([0-9]+)$", socketIpString); // Example: btc.softwareverde.com/0.0.0.0:8333
                    // final String domainName = urlParts.get(0);
                    _remoteIp = urlParts.get(1); // Ip Address
                    // final String portString = urlParts.get(2);
                }

                _binarySocket = new BinarySocket(socket, _binaryPacketFormat);
                _onSocketConnected();
            }
        }
    }

    private final String _host;
    private final Integer _port;
    private String _remoteIp;
    private final BinaryPacketFormat _binaryPacketFormat;

    private final LinkedList<ProtocolMessage> _outboundMessageQueue = new LinkedList<ProtocolMessage>();

    private BinarySocket _binarySocket;
    private Thread _connectionThread;
    private MessageReceivedCallback _messageReceivedCallback;

    private Boolean _socketUsedToBeConnected = false;

    private Long _connectionCount = 0L;
    private Runnable _onDisconnectCallback;
    private Runnable _onReconnectCallback;
    private Runnable _onConnectCallback;

    private Boolean _socketIsConnected() {
        return ( (_binarySocket != null) && (_binarySocket.isConnected()) );
    }

    private void _onSocketConnected() {
        _binarySocket.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                if (_messageReceivedCallback != null) {
                    _messageReceivedCallback.onMessageReceived(_binarySocket.popMessage());
                }
            }
        });

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

    private void _processOutboundMessageQueue() {
        synchronized (_outboundMessageQueue) {
            while (! _outboundMessageQueue.isEmpty()) {
                if ((_binarySocket == null) || (! _binarySocket.isConnected())) { return; }

                final ProtocolMessage message = _outboundMessageQueue.removeFirst();
                _binarySocket.write(message);
            }
        }
    }

    private void _writeOrQueueMessage(final ProtocolMessage message) {
        synchronized (_outboundMessageQueue) {
            if (_socketIsConnected()) {
                _binarySocket.write(message);
            }
            else {
                _outboundMessageQueue.addLast(message);
            }
        }
    }

    public NodeConnection(final String host, final Integer port, final BinaryPacketFormat binaryPacketFormat) {
        _host = host;
        _port = port;

        _connectionThread = new ConnectionThread();
        _binaryPacketFormat = binaryPacketFormat;
    }

    public NodeConnection(final BinarySocket binarySocket) {
        _host = binarySocket.getHost();
        _port = binarySocket.getPort();
        _binarySocket = binarySocket;
        _connectionThread = new ConnectionThread();
        _binaryPacketFormat = binarySocket.getBinaryPacketFormat();

        _onSocketConnected();
    }

    public void startConnectionThread() {
        _connectionThread.start();
    }

    public void stopConnectionThread() {
        _connectionThread.interrupt();
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

    public Boolean isConnected() {
        return _socketIsConnected();
    }

    public void disconnect() {
        _connectionThread.interrupt();

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
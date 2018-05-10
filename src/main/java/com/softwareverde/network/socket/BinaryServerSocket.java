package com.softwareverde.network.socket;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;

import java.io.IOException;

public class BinaryServerSocket<S> {
    public interface SocketEventCallback {
        void onConnect(BinarySocket socketConnection);
        void onDisconnect(BinarySocket socketConnection);
    }

    protected class ServerThread extends Thread {
        public ServerThread() {
            this.setName("Bitcoin Server Socket - Server Thread - " + this.getId());
        }

        @Override
        public void run() {
            try {
                while (_shouldContinue) {
                    if (_socket == null) { return; }

                    final BinarySocket connection = new BinarySocket(_socket.accept(), _binaryPacketFormat);

                    final Boolean shouldPurgeConnections = (_nextConnectionId % PURGE_EVERY_COUNT == 0L);
                    if (shouldPurgeConnections) {
                        _purgeDisconnectedConnections();
                    }

                    synchronized (_connections) {
                        _connections.add(connection);
                        _nextConnectionId += 1L;
                    }

                    _onConnect(connection);
                }
            }
            catch (final IOException exception) { }
        }
    }

    protected static final Long PURGE_EVERY_COUNT = 20L;

    protected final Integer _port;
    protected final BinaryPacketFormat _binaryPacketFormat;
    protected java.net.ServerSocket _socket;

    protected final MutableList<BinarySocket> _connections = new MutableList<BinarySocket>();

    protected Long _nextConnectionId = 0L;
    protected volatile Boolean _shouldContinue = true;
    protected Thread _serverThread = null;

    protected SocketEventCallback _socketEventCallback = null;

    protected void _purgeDisconnectedConnections() {
        final Integer socketCount = _connections.getSize();
        final MutableList<BinarySocket> connectedSockets = new MutableList<BinarySocket>(socketCount);
        final MutableList<BinarySocket> disconnectedSockets = new MutableList<BinarySocket>(socketCount);

        synchronized (_connections) {
            int socketIndex = 0;
            for (final BinarySocket connection : _connections) {
                if (connection.isConnected()) {
                    connectedSockets.add(connection);
                }
                else {
                    disconnectedSockets.add(connection);
                    Logger.log("Marking socket as disconnected: "+ socketIndex);
                }

                socketIndex += 1;
            }

            _connections.clear();
            _connections.addAll(connectedSockets);
        }

        for (final BinarySocket bitcoinSocket : disconnectedSockets) {
            Logger.log("Purging disconnected socket.");
            _onDisconnect(bitcoinSocket);
        }
    }

    protected void _onConnect(final BinarySocket socketConnection) {
        final SocketEventCallback socketEventCallback = _socketEventCallback;

        if (socketEventCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    socketEventCallback.onConnect(socketConnection);
                }
            })).start();
        }
    }

    protected void _onDisconnect(final BinarySocket socketConnection) {
        final SocketEventCallback socketEventCallback = _socketEventCallback;

        if (socketEventCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    socketEventCallback.onDisconnect(socketConnection);
                }
            })).start();
        }
    }

    public BinaryServerSocket(final Integer port, final BinaryPacketFormat binaryPacketFormat) {
        _port = port;
        _binaryPacketFormat = binaryPacketFormat;
    }

    public void setSocketEventCallback(final SocketEventCallback socketEventCallback) {
        _socketEventCallback = socketEventCallback;
    }

    public void start() {
        _shouldContinue = true;

        try {
            _socket = new java.net.ServerSocket(_port);

            _serverThread = new ServerThread();
            _serverThread.start();

        }
        catch (final IOException exception) { }
    }

    public void stop() {
        _shouldContinue = false;

        if (_socket != null) {
            try {
                _socket.close();
            }
            catch (final IOException e) { }
        }

        _socket = null;

        try {
            if (_serverThread != null) {
                _serverThread.join();
            }
        }
        catch (final Exception exception) { }
    }
}

package com.softwareverde.network.socket;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;

import java.io.IOException;

public class SocketServer {
    public interface SocketConnectedCallback {
        void run(BinarySocket socketConnection);
    }

    public interface SocketDisconnectedCallback {
        void run(BinarySocket socketConnection);
    }

    protected class ServerThread extends Thread {
        public ServerThread() {
            this.setName("Socket Server - Server Thread - " + this.getId());
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
            catch (final Exception exception) { }
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

    protected SocketConnectedCallback _socketConnectedCallback = null;
    protected SocketDisconnectedCallback _socketDisconnectedCallback = null;

    protected void _purgeDisconnectedConnections() {
        final Integer socketCount = _connections.getSize();
        final MutableList<BinarySocket> disconnectedSockets = new MutableList<BinarySocket>(socketCount);

        synchronized (_connections) {
            int socketIndex = 0;
            while (socketIndex < _connections.getSize()) {
                final BinarySocket connection = _connections.get(socketIndex);

                if (! connection.isConnected()) {
                    _connections.remove(socketIndex);
                    disconnectedSockets.add(connection);
                    Logger.log("Marking socket as disconnected: "+ socketIndex);
                }
                else {
                    socketIndex += 1;
                }
            }
        }

        for (final BinarySocket bitcoinSocket : disconnectedSockets) {
            Logger.log("Purging disconnected socket.");
            _onDisconnect(bitcoinSocket);
        }
    }

    protected void _onConnect(final BinarySocket socketConnection) {
        final SocketConnectedCallback socketConnectedCallback = _socketConnectedCallback;

        if (socketConnectedCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    socketConnectedCallback.run(socketConnection);
                }
            })).start();
        }
    }

    protected void _onDisconnect(final BinarySocket socketConnection) {
        final SocketDisconnectedCallback socketDisconnectedCallback = _socketDisconnectedCallback;

        if (socketDisconnectedCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    socketDisconnectedCallback.run(socketConnection);
                }
            })).start();
        }
    }

    public SocketServer(final Integer port, final BinaryPacketFormat binaryPacketFormat) {
        _port = port;
        _binaryPacketFormat = binaryPacketFormat;
    }

    public void setSocketConnectedCallback(final SocketConnectedCallback socketConnectedCallback) {
        _socketConnectedCallback = socketConnectedCallback;
    }

    public void setSocketDisconnectedCallback(final SocketDisconnectedCallback socketDisconnectedCallback) {
        _socketDisconnectedCallback = socketDisconnectedCallback;
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
            _serverThread.interrupt();
            if (_serverThread != null) {
                _serverThread.join();
            }
        }
        catch (final Exception exception) { }
    }
}

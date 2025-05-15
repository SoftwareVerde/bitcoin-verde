package com.softwareverde.network.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;

import java.net.Socket;

public class BinarySocketServer extends SocketServer<BinarySocket> {

    protected static class BinarySocketFactory implements SocketFactory<BinarySocket> {
        protected final BinaryPacketFormat _binaryPacketFormat;

        public BinarySocketFactory(final BinaryPacketFormat binaryPacketFormat) {
            _binaryPacketFormat = binaryPacketFormat;
        }

        @Override
        public BinarySocket newSocket(final Socket socket) {
            return new BinarySocket(socket, _binaryPacketFormat);
        }
    }

    public interface SocketConnectedCallback extends SocketServer.SocketConnectedCallback<BinarySocket> {
        @Override
        void run(BinarySocket socketConnection);
    }

    public interface SocketDisconnectedCallback extends SocketServer.SocketDisconnectedCallback<BinarySocket> {
        @Override
        void run(BinarySocket socketConnection);
    }

    protected final BinaryPacketFormat _binaryPacketFormat;

    public BinarySocketServer(final Integer port, final BinaryPacketFormat binaryPacketFormat) {
        super(port, new BinarySocketFactory(binaryPacketFormat));
        _binaryPacketFormat = binaryPacketFormat;
    }
}

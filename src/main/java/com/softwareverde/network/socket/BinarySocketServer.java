package com.softwareverde.network.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;

import java.net.Socket;

public class BinarySocketServer extends SocketServer<BinarySocket> {

    protected static class BinarySocketFactory implements SocketFactory<BinarySocket> {
        protected final BinaryPacketFormat _binaryPacketFormat;
        protected final ThreadPool _threadPool;

        public BinarySocketFactory(final BinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool) {
            _binaryPacketFormat = binaryPacketFormat;
            _threadPool = threadPool;
        }

        @Override
        public BinarySocket newSocket(final Socket socket) {
            return new BinarySocket(socket, _binaryPacketFormat, _threadPool);
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

    public BinarySocketServer(final Integer port, final BinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool) {
        super(port, new BinarySocketFactory(binaryPacketFormat, threadPool), threadPool);
        _binaryPacketFormat = binaryPacketFormat;
    }
}

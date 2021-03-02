package com.softwareverde.network.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;

import java.net.Socket;

public class JsonSocketServer extends SocketServer<JsonSocket> {

    protected static class JsonSocketFactory implements SocketFactory<JsonSocket> {
        protected final ThreadPool _threadPool;

        public JsonSocketFactory(final ThreadPool threadPool) {
            _threadPool = threadPool;
        }

        @Override
        public JsonSocket newSocket(final Socket socket) {
            return new JsonSocket(socket, _threadPool);
        }
    }

    public interface SocketConnectedCallback extends SocketServer.SocketConnectedCallback<JsonSocket> {
        @Override
        void run(JsonSocket socketConnection);
    }
    public interface SocketDisconnectedCallback extends SocketServer.SocketDisconnectedCallback<JsonSocket> {
        @Override
        void run(JsonSocket socketConnection);
    }

    public JsonSocketServer(final Integer port, final ThreadPool threadPool) {
        super(port, new JsonSocketFactory(threadPool), threadPool);
    }
}

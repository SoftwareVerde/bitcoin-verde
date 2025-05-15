package com.softwareverde.network.socket;

import java.net.Socket;

public class JsonSocketServer extends SocketServer<JsonSocket> {

    protected static class JsonSocketFactory implements SocketFactory<JsonSocket> {
        public JsonSocketFactory() { }

        @Override
        public JsonSocket newSocket(final Socket socket) {
            return new JsonSocket(socket);
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

    public JsonSocketServer(final Integer port) {
        super(port, new JsonSocketFactory());
    }
}

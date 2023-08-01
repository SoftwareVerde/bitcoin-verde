package com.softwareverde.network.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;

public class JsonSocket extends Socket {
    public JsonSocket(final java.net.Socket socket) {
        super(socket, new JsonSocketReadThread(), new JsonSocketWriteThread());
    }

    public JsonSocket(final java.net.Socket socket, final ThreadPool threadPool) {
        super(socket, new JsonSocketReadThread(), new JsonSocketWriteThread());
    }

    @Override
    public JsonProtocolMessage popMessage() {
        return (JsonProtocolMessage) super.popMessage();
    }
}

package com.softwareverde.network.socket;

public class JsonSocket extends Socket {
    public JsonSocket(final java.net.Socket socket) {
        super(socket, new JsonSocketReadThread(), new JsonSocketWriteThread());
    }

    @Override
    public JsonProtocolMessage popMessage() {
        return (JsonProtocolMessage) super.popMessage();
    }
}

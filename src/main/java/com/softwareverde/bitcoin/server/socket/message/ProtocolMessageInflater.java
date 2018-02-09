package com.softwareverde.bitcoin.server.socket.message;

public interface ProtocolMessageInflater {
    ProtocolMessage fromBytes(byte[] bytes);
}

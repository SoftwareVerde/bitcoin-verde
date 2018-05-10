package com.softwareverde.network.p2p.message;

public interface ProtocolMessageFactory<T extends ProtocolMessage> {
    T fromBytes(byte[] bytes);
}

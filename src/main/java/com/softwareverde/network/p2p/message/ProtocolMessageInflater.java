package com.softwareverde.network.p2p.message;

public interface ProtocolMessageInflater<T extends ProtocolMessage> {
    T fromBytes(byte[] bytes);
}

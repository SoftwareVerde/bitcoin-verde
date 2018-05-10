package com.softwareverde.network.p2p.message.type;

import com.softwareverde.network.p2p.message.ProtocolMessage;

public interface PongMessage<T> extends ProtocolMessage<T> {
    Long getNonce();
}

package com.softwareverde.network.p2p.message.type;

import com.softwareverde.network.p2p.message.ProtocolMessage;

public interface PingMessage extends ProtocolMessage {
    Long getNonce();
}

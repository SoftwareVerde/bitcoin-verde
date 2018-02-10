package com.softwareverde.bitcoin.server.socket.message.version.acknowledge;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;

public class AcknowledgeVersionMessage extends ProtocolMessage {

    public AcknowledgeVersionMessage() {
        super(MessageType.ACKNOWLEDGE_VERSION);
    }

    @Override
    protected byte[] _getPayload() {
        return new byte[0];
    }
}

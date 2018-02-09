package com.softwareverde.bitcoin.server.socket.message.version.acknowledge;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;

public class AcknowledgeVersionMessage extends ProtocolMessage {

    public AcknowledgeVersionMessage() {
        super(Command.ACKNOWLEDGE_VERSION);
    }

}

package com.softwareverde.bitcoin.server.message.type.version.acknowledge;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.bytearray.MutableByteArray;

public class AcknowledgeVersionMessage extends ProtocolMessage {

    public AcknowledgeVersionMessage() {
        super(MessageType.ACKNOWLEDGE_VERSION);
    }

    @Override
    protected ByteArray _getPayload() {
        return new MutableByteArray(0);
    }
}

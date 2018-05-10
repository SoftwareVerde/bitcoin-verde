package com.softwareverde.bitcoin.server.message.type.version.acknowledge;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class AcknowledgeVersionMessage extends BitcoinProtocolMessage {

    public AcknowledgeVersionMessage() {
        super(MessageType.ACKNOWLEDGE_VERSION);
    }

    @Override
    protected ByteArray _getPayload() {
        return new MutableByteArray(0);
    }
}

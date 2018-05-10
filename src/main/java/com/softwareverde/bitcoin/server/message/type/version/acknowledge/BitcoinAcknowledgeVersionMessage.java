package com.softwareverde.bitcoin.server.message.type.version.acknowledge;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.network.p2p.message.type.AcknowledgeVersionMessage;

public class BitcoinAcknowledgeVersionMessage extends BitcoinProtocolMessage implements AcknowledgeVersionMessage<MessageType> {

    public BitcoinAcknowledgeVersionMessage() {
        super(MessageType.ACKNOWLEDGE_VERSION);
    }

    @Override
    protected ByteArray _getPayload() {
        return new MutableByteArray(0);
    }
}

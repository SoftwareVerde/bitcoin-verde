package com.softwareverde.bitcoin.server.message.type.node.address.request;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class RequestPeersMessage extends BitcoinProtocolMessage {

    public RequestPeersMessage() {
        super(MessageType.REQUEST_PEERS);
    }

    @Override
    protected ByteArray _getPayload() {
        return new MutableByteArray(0);
    }
}

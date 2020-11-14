package com.softwareverde.bitcoin.server.message.type.node.feature;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class NewBlocksViaHeadersMessage extends BitcoinProtocolMessage {

    public NewBlocksViaHeadersMessage() {
        super(MessageType.ENABLE_NEW_BLOCKS_VIA_HEADERS);
    }

    @Override
    protected ByteArray _getPayload() {
        return new MutableByteArray(0);
    }

    @Override
    protected Integer _getPayloadByteCount() {
        return 0;
    }
}

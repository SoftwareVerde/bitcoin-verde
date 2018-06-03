package com.softwareverde.bitcoin.server.message.type.node.feature;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class NewBlocksViaHeadersMessage extends BitcoinProtocolMessage {

    public NewBlocksViaHeadersMessage() {
        super(MessageType.ENABLE_NEW_BLOCKS_VIA_HEADERS);
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}

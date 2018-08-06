package com.softwareverde.bitcoin.server.message.type.node.feature;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class NewBlocksViaHeadersMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public NewBlocksViaHeadersMessage fromBytes(final byte[] bytes) {
        final NewBlocksViaHeadersMessage enableDirectHeadersMessage = new NewBlocksViaHeadersMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.ENABLE_NEW_BLOCKS_VIA_HEADERS);
        if (protocolMessageHeader == null) { return null; }

        if (byteArrayReader.didOverflow()) { return null; }

        return enableDirectHeadersMessage;
    }
}

package com.softwareverde.bitcoin.server.message.type.version.acknowledge;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class AcknowledgeVersionMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public AcknowledgeVersionMessage fromBytes(final byte[] bytes) {
        final AcknowledgeVersionMessage synchronizeVersionMessage = new AcknowledgeVersionMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.ACKNOWLEDGE_VERSION);
        if (protocolMessageHeader == null) { return null; }

        return synchronizeVersionMessage;
    }

}

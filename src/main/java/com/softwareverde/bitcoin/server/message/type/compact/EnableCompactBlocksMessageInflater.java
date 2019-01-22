package com.softwareverde.bitcoin.server.message.type.compact;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class EnableCompactBlocksMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public EnableCompactBlocksMessage fromBytes(final byte[] bytes) {
        final EnableCompactBlocksMessage enableCompactBlocksMessage = new EnableCompactBlocksMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.ENABLE_COMPACT_BLOCKS);
        if (protocolMessageHeader == null) { return null; }

        final Boolean isEnabled = (byteArrayReader.readInteger(4, Endian.LITTLE) > 0);
        final Integer version = byteArrayReader.readInteger(4, Endian.LITTLE);

        enableCompactBlocksMessage.setIsEnabled(isEnabled);
        enableCompactBlocksMessage.setVersion(version);

        if (byteArrayReader.didOverflow()) { return null; }

        return enableCompactBlocksMessage;
    }
}

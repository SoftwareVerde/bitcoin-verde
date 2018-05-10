package com.softwareverde.bitcoin.server.message.type.block;

import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class BlockMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public BlockMessage fromBytes(final byte[] bytes) {
        final BlockMessage blockMessage = new BlockMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.BLOCK);
        if (protocolMessageHeader == null) { return null; }

        final BlockInflater blockInflater = new BlockInflater();
        blockMessage._block = blockInflater.fromBytes(byteArrayReader);

        return blockMessage;
    }
}

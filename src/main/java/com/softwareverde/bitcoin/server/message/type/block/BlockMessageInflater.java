package com.softwareverde.bitcoin.server.message.type.block;

import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeader;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class BlockMessageInflater extends ProtocolMessageInflater {
    @Override
    public BlockMessage fromBytes(final byte[] bytes) {
        final BlockMessage blockMessage = new BlockMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.BLOCK);
        if (protocolMessageHeader == null) { return null; }

        final BlockInflater blockInflater = new BlockInflater();
        blockMessage._block = blockInflater.fromBytes(byteArrayReader);

        return blockMessage;
    }
}

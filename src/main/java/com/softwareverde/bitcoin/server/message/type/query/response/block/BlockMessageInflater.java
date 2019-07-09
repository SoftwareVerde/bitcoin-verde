package com.softwareverde.bitcoin.server.message.type.query.response.block;

import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class BlockMessageInflater extends BitcoinProtocolMessageInflater {
    protected final BlockInflaters _blockInflaters;

    public BlockMessageInflater(final BlockInflaters blockInflaters) {
        _blockInflaters = blockInflaters;
    }

    @Override
    public BlockMessage fromBytes(final byte[] bytes) {
        final BlockMessage blockMessage = new BlockMessage(_blockInflaters);
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.BLOCK);
        if (protocolMessageHeader == null) { return null; }

        final BlockInflater blockInflater = _blockInflaters.getBlockInflater();
        blockMessage._block = blockInflater.fromBytes(byteArrayReader);

        return blockMessage;
    }
}

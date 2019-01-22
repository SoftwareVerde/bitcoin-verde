package com.softwareverde.bitcoin.server.message.type.query.response.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;

public class BlockMessage extends BitcoinProtocolMessage {

    protected Block _block;

    public BlockMessage() {
        super(MessageType.BLOCK);
    }

    public Block getBlock() {
        return _block;
    }

    public void setBlock(final Block block) {
        _block = block;
    }

    @Override
    protected ByteArray _getPayload() {
        final BlockDeflater blockDeflater = new BlockDeflater();
        return blockDeflater.toBytes(_block);
    }
}

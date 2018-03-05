package com.softwareverde.bitcoin.server.message.type.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.server.message.ProtocolMessage;

public class BlockMessage extends ProtocolMessage {

    protected Block _block;

    public BlockMessage() {
        super(MessageType.BLOCK);
    }

    public Block getBlock() {
        return _block;
    }

    @Override
    protected byte[] _getPayload() {
        final BlockDeflater blockDeflater = new BlockDeflater();
        return blockDeflater.toBytes(_block);
    }
}

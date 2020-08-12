package com.softwareverde.bitcoin.server.message.type.query.response.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class BlockMessage extends BitcoinProtocolMessage {

    protected final BlockInflaters _blockInflaters;
    protected Block _block;

    public BlockMessage(final BlockInflaters blockInflaters) {
        super(MessageType.BLOCK);
        _blockInflaters = blockInflaters;
    }


    public Block getBlock() {
        return _block;
    }

    public void setBlock(final Block block) {
        _block = block;
    }

    @Override
    protected ByteArray _getPayload() {
        if (_block == null) {
            return new MutableByteArray(0);
        }

        final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
        return blockDeflater.toBytes(_block);
    }
}

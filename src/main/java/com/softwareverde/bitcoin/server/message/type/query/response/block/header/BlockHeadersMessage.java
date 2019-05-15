package com.softwareverde.bitcoin.server.message.type.query.response.block.header;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class BlockHeadersMessage extends BitcoinProtocolMessage {
    protected final MutableList<BlockHeader> _blockHeaders = new MutableList<>();

    public BlockHeadersMessage() {
        super(MessageType.BLOCK_HEADERS);
    }

    public void addBlockHeader(final BlockHeader blockHeader) {
        if (_blockHeaders.getSize() >= RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return; }
        _blockHeaders.add(blockHeader);
    }

    public void clearBlockHeaders() {
        _blockHeaders.clear();
    }

    public List<BlockHeader> getBlockHeaders() {
        return _blockHeaders;
    }

    @Override
    protected ByteArray _getPayload() {
        final int blockHeaderCount = _blockHeaders.getSize();

        final byte[] blockHeaderCountBytes = ByteUtil.variableLengthIntegerToBytes(blockHeaderCount);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(blockHeaderCountBytes);

        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        for (int i=0; i<blockHeaderCount; ++i) {
            final BlockHeader blockHeader = _blockHeaders.get(i);
            byteArrayBuilder.appendBytes(blockHeaderDeflater.toBytes(blockHeader));

            final Integer transactionCount = 0; // blockHeader.getTransactionCount();
            final byte[] transactionCountBytes = ByteUtil.variableLengthIntegerToBytes(transactionCount);
            byteArrayBuilder.appendBytes(transactionCountBytes);
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}

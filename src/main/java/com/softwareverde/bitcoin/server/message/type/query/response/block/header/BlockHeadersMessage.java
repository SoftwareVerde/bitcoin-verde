package com.softwareverde.bitcoin.server.message.type.query.response.block.header;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.inflater.BlockHeaderInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class BlockHeadersMessage extends BitcoinProtocolMessage {
    protected final BlockHeaderInflaters _blockHeaderInflaters;
    protected final MutableList<BlockHeader> _blockHeaders = new MutableArrayList<>();

    public BlockHeadersMessage(final BlockHeaderInflaters blockHeaderInflaters) {
        super(MessageType.BLOCK_HEADERS);
        _blockHeaderInflaters = blockHeaderInflaters;
    }

    public void addBlockHeader(final BlockHeader blockHeader) {
        if (_blockHeaders.getCount() >= RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return; }
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
        final int blockHeaderCount = _blockHeaders.getCount();

        final ByteArray blockHeaderCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(blockHeaderCount);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(blockHeaderCountBytes);

        final BlockHeaderDeflater blockHeaderDeflater = _blockHeaderInflaters.getBlockHeaderDeflater();
        for (int i = 0; i < blockHeaderCount; ++i) {
            final BlockHeader blockHeader = _blockHeaders.get(i);
            byteArrayBuilder.appendBytes(blockHeaderDeflater.toBytes(blockHeader));

            final int transactionCount = 0; // blockHeader.getTransactionCount();
            final ByteArray transactionCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(transactionCount);
            byteArrayBuilder.appendBytes(transactionCountBytes);
        }

        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final int blockHeaderCount = _blockHeaders.getCount();
        final ByteArray blockHeaderCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(blockHeaderCount);

        return (blockHeaderCountBytes.getByteCount() + (blockHeaderCount * (BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT + 1)));
    }
}

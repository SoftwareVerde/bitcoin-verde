package com.softwareverde.bitcoin.server.message.type.query.response.block.header;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCountInflater;
import com.softwareverde.bitcoin.inflater.ExtendedBlockHeaderInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.logging.Logger;

public class BlockHeadersMessageInflater extends BitcoinProtocolMessageInflater {

    protected final ExtendedBlockHeaderInflaters _blockHeaderInflaters;

    public BlockHeadersMessageInflater(final ExtendedBlockHeaderInflaters blockHeaderInflaters) {
        _blockHeaderInflaters = blockHeaderInflaters;
    }

    @Override
    public BlockHeadersMessage fromBytes(final byte[] bytes) {
        final BlockHeadersMessage blockHeadersResponseMessage = new BlockHeadersMessage(_blockHeaderInflaters);
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.BLOCK_HEADERS);
        if (protocolMessageHeader == null) { return null; }

        final Integer blockHeaderCount = byteArrayReader.readVariableLengthInteger().intValue();
        if (blockHeaderCount > RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) {
            Logger.debug("Block Header Count Exceeded: " + blockHeaderCount);
            return null;
        }

        final Integer bytesRequired = ( blockHeaderCount * (BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT + 1) );
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        final BlockHeaderWithTransactionCountInflater blockHeaderInflater = _blockHeaderInflaters.getBlockHeaderWithTransactionCountInflater();

        // NOTE: The BlockHeaders message always appends a zero variable-sized-integer to represent the TransactionCount.
        for (int i = 0; i < blockHeaderCount; ++i) {
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
            if (blockHeader == null) { return null; }

            blockHeadersResponseMessage._blockHeaders.add(blockHeader);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return blockHeadersResponseMessage;
    }
}

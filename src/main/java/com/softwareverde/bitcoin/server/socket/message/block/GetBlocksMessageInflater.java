package com.softwareverde.bitcoin.server.socket.message.block;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class GetBlocksMessageInflater extends ProtocolMessageInflater {

    @Override
    public GetBlocksMessage fromBytes(final byte[] bytes) {
        final Integer blockHeaderHashByteCount = 32;
        final GetBlocksMessage getBlocksMessage = new GetBlocksMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.GET_BLOCKS);
        if (protocolMessageHeader == null) { return null; }

        getBlocksMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer blockHeaderCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (blockHeaderCount >= GetBlocksMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return null; }

        final Integer bytesRequired = (blockHeaderCount * blockHeaderHashByteCount);
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        for (int i=0; i<blockHeaderCount; ++i) {
            final byte[] blockHeaderHashBytes = byteArrayReader.readBytes(32, Endian.BIG);
            getBlocksMessage._blockHeaderHashes.add(blockHeaderHashBytes);
        }

        final byte[] blockHeaderHashBytes = byteArrayReader.readBytes(32, Endian.BIG);
        ByteUtil.setBytes(getBlocksMessage._desiredBlockHeaderHash, blockHeaderHashBytes);

        return getBlocksMessage;
    }
}

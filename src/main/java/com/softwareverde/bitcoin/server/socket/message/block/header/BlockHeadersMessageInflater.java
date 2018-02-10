package com.softwareverde.bitcoin.server.socket.message.block.header;

import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class BlockHeadersMessageInflater extends ProtocolMessageInflater {

    @Override
    public BlockHeadersMessage fromBytes(final byte[] bytes) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final BlockHeadersMessage blockHeadersMessage = new BlockHeadersMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.BLOCK_HEADERS);
        if (protocolMessageHeader == null) { return null; }

        blockHeadersMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer blockHeaderCount = byteArrayReader.readVariableSizedInteger().intValue();
        final Integer bytesRequired = (blockHeaderCount * BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        for (int i=0; i<blockHeaderCount; ++i) {
            final byte[] blockHeaderBytes = byteArrayReader.readBytes(32, Endian.BIG);
            blockHeadersMessage._blockHeaders.add(blockHeaderInflater.fromBytes(blockHeaderBytes));
        }

        final byte[] blockHeaderBytes = byteArrayReader.readBytes(32, Endian.BIG);
        blockHeadersMessage._desiredBlockHeader = blockHeaderInflater.fromBytes(blockHeaderBytes);

        return blockHeadersMessage;
    }
}

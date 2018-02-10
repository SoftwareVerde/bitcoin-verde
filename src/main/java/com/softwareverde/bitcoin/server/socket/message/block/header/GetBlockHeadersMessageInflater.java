package com.softwareverde.bitcoin.server.socket.message.block.header;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class GetBlockHeadersMessageInflater extends ProtocolMessageInflater {

    @Override
    public GetBlockHeadersMessage fromBytes(final byte[] bytes) {
        final Integer blockHeaderHashByteCount = 32;
        final GetBlockHeadersMessage getBlockHeadersMessage = new GetBlockHeadersMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.GET_BLOCK_HEADERS);
        if (protocolMessageHeader == null) { return null; }

        getBlockHeadersMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer blockHeaderCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (blockHeaderCount >= GetBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return null; }

        final Integer bytesRequired = (blockHeaderCount * blockHeaderHashByteCount);
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        for (int i=0; i<blockHeaderCount; ++i) {
            final byte[] blockHeaderHashBytes = byteArrayReader.readBytes(32, Endian.BIG);
            getBlockHeadersMessage._blockHeaderHashes.add(blockHeaderHashBytes);
        }

        final byte[] blockHeaderHashBytes = byteArrayReader.readBytes(32, Endian.BIG);
        ByteUtil.setBytes(getBlockHeadersMessage._desiredBlockHeaderHash, blockHeaderHashBytes);

        return getBlockHeadersMessage;
    }
}

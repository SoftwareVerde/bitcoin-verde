package com.softwareverde.bitcoin.server.message.type.request.header;

import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class RequestBlockHeadersMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public RequestBlockHeadersMessage fromBytes(final byte[] bytes) {
        final Integer blockHeaderHashByteCount = 32;
        final RequestBlockHeadersMessage requestBlockHeadersMessage = new RequestBlockHeadersMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.REQUEST_BLOCK_HEADERS);
        if (protocolMessageHeader == null) { return null; }

        requestBlockHeadersMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer blockHeaderCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (blockHeaderCount > RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return null; }

        final Integer bytesRequired = (blockHeaderCount * blockHeaderHashByteCount);
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        for (int i = 0; i < blockHeaderCount; ++i) {
            final byte[] blockHeaderHashBytes = byteArrayReader.readBytes(32, Endian.LITTLE);
            requestBlockHeadersMessage._blockHeaderHashes.add(MutableSha256Hash.wrap(blockHeaderHashBytes));
        }

        final byte[] blockHeaderHashBytes = byteArrayReader.readBytes(32, Endian.LITTLE);
        requestBlockHeadersMessage.setStopBeforeBlockHash(MutableSha256Hash.wrap(blockHeaderHashBytes));

        if (byteArrayReader.didOverflow()) { return null; }

        return requestBlockHeadersMessage;
    }
}

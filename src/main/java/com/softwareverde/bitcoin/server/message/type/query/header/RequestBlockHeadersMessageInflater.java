package com.softwareverde.bitcoin.server.message.type.query.header;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class RequestBlockHeadersMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public RequestBlockHeadersMessage fromBytes(final byte[] bytes) {
        final int blockHeaderHashByteCount = 32;
        final RequestBlockHeadersMessage requestBlockHeadersMessage = new RequestBlockHeadersMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.REQUEST_BLOCK_HEADERS);
        if (protocolMessageHeader == null) { return null; }

        requestBlockHeadersMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final CompactVariableLengthInteger blockHeaderCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! blockHeaderCount.isCanonical()) { return null; }
        if (blockHeaderCount.value > RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return null; }

        final Integer bytesRequired = (blockHeaderCount.intValue() * blockHeaderHashByteCount);
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        for (int i = 0; i < blockHeaderCount.value; ++i) {
            final Sha256Hash blockHeaderHashBytes = Sha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
            requestBlockHeadersMessage.addBlockHash(blockHeaderHashBytes);
        }

        final Sha256Hash blockHeaderHash = Sha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
        requestBlockHeadersMessage.setStopBeforeBlockHash(blockHeaderHash);

        if (byteArrayReader.didOverflow()) { return null; }

        return requestBlockHeadersMessage;
    }
}

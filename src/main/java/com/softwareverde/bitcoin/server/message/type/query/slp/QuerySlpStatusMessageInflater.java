package com.softwareverde.bitcoin.server.message.type.query.slp;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class QuerySlpStatusMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public BitcoinProtocolMessage fromBytes(final byte[] bytes) {
        final QuerySlpStatusMessage queryAddressBlocksMessage = new QuerySlpStatusMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.QUERY_SLP_STATUS);
        if (protocolMessageHeader == null) { return null; }

        final int addressCount = byteArrayReader.readVariableLengthInteger().intValue();
        if ( (addressCount < 0) || (addressCount >= QuerySlpStatusMessage.MAX_HASH_COUNT) ) { return null; }

        final Integer bytesRequired = (Sha256Hash.BYTE_COUNT * addressCount);
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        for (int i = 0; i < addressCount; ++i) {
            final Sha256Hash hash = MutableSha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
            queryAddressBlocksMessage.addHash(hash);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return queryAddressBlocksMessage;
    }
}

package com.softwareverde.bitcoin.server.message.type.bloomfilter.update;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;

public class UpdateTransactionBloomFilterMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public UpdateTransactionBloomFilterMessage fromBytes(final byte[] bytes) {
        final UpdateTransactionBloomFilterMessage updateTransactionBloomFilterMessage = new UpdateTransactionBloomFilterMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.UPDATE_TRANSACTION_BLOOM_FILTER);
        if (protocolMessageHeader == null) { return null; }

        final ByteArray item = new ImmutableByteArray(byteArrayReader.readBytes(byteArrayReader.remainingByteCount()));
        updateTransactionBloomFilterMessage.setItem(item);

        if (byteArrayReader.didOverflow()) { return null; }

        return updateTransactionBloomFilterMessage;
    }
}

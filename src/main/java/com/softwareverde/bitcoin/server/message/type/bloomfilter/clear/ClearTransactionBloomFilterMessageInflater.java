package com.softwareverde.bitcoin.server.message.type.bloomfilter.clear;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class ClearTransactionBloomFilterMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public ClearTransactionBloomFilterMessage fromBytes(final byte[] bytes) {
        final ClearTransactionBloomFilterMessage clearTransactionBloomFilterMessage = new ClearTransactionBloomFilterMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.CLEAR_TRANSACTION_BLOOM_FILTER);
        if (protocolMessageHeader == null) { return null; }

        return clearTransactionBloomFilterMessage;
    }
}

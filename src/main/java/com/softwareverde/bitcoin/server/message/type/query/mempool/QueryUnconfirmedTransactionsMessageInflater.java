package com.softwareverde.bitcoin.server.message.type.query.mempool;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class QueryUnconfirmedTransactionsMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public QueryUnconfirmedTransactionsMessage fromBytes(final byte[] bytes) {
        final QueryUnconfirmedTransactionsMessage queryUnconfirmedTransactionsMessage = new QueryUnconfirmedTransactionsMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.QUERY_UNCONFIRMED_TRANSACTIONS);
        if (protocolMessageHeader == null) { return null; }

        return queryUnconfirmedTransactionsMessage;
    }
}

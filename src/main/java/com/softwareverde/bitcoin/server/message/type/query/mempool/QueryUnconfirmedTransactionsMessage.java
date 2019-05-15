package com.softwareverde.bitcoin.server.message.type.query.mempool;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class QueryUnconfirmedTransactionsMessage extends BitcoinProtocolMessage {

    public QueryUnconfirmedTransactionsMessage() {
        super(MessageType.QUERY_UNCONFIRMED_TRANSACTIONS);
    }

    @Override
    protected ByteArray _getPayload() {
        return new MutableByteArray(0);
    }
}

package com.softwareverde.bitcoin.server.message.type.bloomfilter.clear;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class ClearTransactionBloomFilterMessage extends BitcoinProtocolMessage {
    public ClearTransactionBloomFilterMessage() {
        super(MessageType.CLEAR_TRANSACTION_BLOOM_FILTER);
    }

    @Override
    protected ByteArray _getPayload() {
        return new MutableByteArray(0);
    }

    @Override
    protected Integer _getPayloadByteCount() {
        return 0;
    }
}

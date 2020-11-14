package com.softwareverde.bitcoin.server.message.type.bloomfilter.update;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;

public class UpdateTransactionBloomFilterMessage extends BitcoinProtocolMessage {
    protected ByteArray _item = null;

    public UpdateTransactionBloomFilterMessage() {
        super(MessageType.UPDATE_TRANSACTION_BLOOM_FILTER);
    }

    public ByteArray getItem() {
        return _item;
    }

    public void setItem(final ByteArray item) {
        _item = item.asConst();
    }

    @Override
    protected ByteArray _getPayload() {
        return _item;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        return _item.getByteCount();
    }
}

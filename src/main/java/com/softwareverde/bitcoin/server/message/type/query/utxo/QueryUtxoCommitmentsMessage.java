package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class QueryUtxoCommitmentsMessage extends BitcoinProtocolMessage {
    public QueryUtxoCommitmentsMessage() {
        super(MessageType.QUERY_UTXO_COMMITMENTS);
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

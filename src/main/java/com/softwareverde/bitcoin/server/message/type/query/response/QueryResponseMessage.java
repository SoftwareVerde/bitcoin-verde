package com.softwareverde.bitcoin.server.message.type.query.response;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class QueryResponseMessage extends BitcoinProtocolMessage {

    private final MutableList<DataHash> _dataHashes = new MutableList<DataHash>();

    public QueryResponseMessage() {
        super(MessageType.QUERY_RESPONSE);
    }

    public List<DataHash> getDataHashes() {
        return _dataHashes;
    }

    public void addInventoryItem(final DataHash dataHash) {
        _dataHashes.add(dataHash);
    }

    public void clearInventoryItems() {
        _dataHashes.clear();
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_dataHashes.getSize()), Endian.BIG);
        for (final DataHash dataHash : _dataHashes) {
            byteArrayBuilder.appendBytes(dataHash.getBytes(), Endian.BIG);
        }
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}

package com.softwareverde.bitcoin.server.message.type.request;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.data.hash.DataHash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class RequestDataMessage extends ProtocolMessage {
    public static final Integer MAX_COUNT = 50000;

    private final List<DataHash> _dataHashes = new ArrayList<DataHash>();

    public RequestDataMessage() {
        super(MessageType.GET_DATA);
    }

    public List<DataHash> getDataHashes() {
        return Util.copyList(_dataHashes);
    }

    public void addInventoryItem(final DataHash dataHash) {
        if (_dataHashes.size() >= MAX_COUNT) { return; }
        _dataHashes.add(dataHash);
    }

    public void clearInventoryItems() {
        _dataHashes.clear();
    }

    @Override
    protected byte[] _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_dataHashes.size()), Endian.LITTLE);
        for (final DataHash dataHash : _dataHashes) {
            byteArrayBuilder.appendBytes(dataHash.getBytes(), Endian.BIG);
        }
        return byteArrayBuilder.build();
    }
}

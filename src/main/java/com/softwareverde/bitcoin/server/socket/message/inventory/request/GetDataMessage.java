package com.softwareverde.bitcoin.server.socket.message.inventory.request;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.inventory.data.header.DataHeader;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class GetDataMessage extends ProtocolMessage {
    public static final Integer MAX_COUNT = 50000;

    private final List<DataHeader> _dataHeaders = new ArrayList<DataHeader>();

    public GetDataMessage() {
        super(MessageType.GET_DATA);
    }

    public List<DataHeader> getDataHeaders() {
        return Util.copyList(_dataHeaders);
    }

    public void addInventoryItem(final DataHeader dataHeader) {
        if (_dataHeaders.size() >= MAX_COUNT) { return; }
        _dataHeaders.add(dataHeader);
    }

    public void clearInventoryItems() {
        _dataHeaders.clear();
    }

    @Override
    protected byte[] _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_dataHeaders.size()), Endian.LITTLE);
        for (final DataHeader dataHeader : _dataHeaders) {
            byteArrayBuilder.appendBytes(dataHeader.getBytes(), Endian.BIG);
        }
        return byteArrayBuilder.build();
    }
}

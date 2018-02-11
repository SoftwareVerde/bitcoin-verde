package com.softwareverde.bitcoin.server.socket.message.inventory.list;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.inventory.data.header.DataHeader;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class InventoryMessage extends ProtocolMessage {

    private final List<DataHeader> _dataHeaders = new ArrayList<DataHeader>();

    public InventoryMessage() {
        super(MessageType.INVENTORY);
    }

    public List<DataHeader> getDataHeaders() {
        return Util.copyList(_dataHeaders);
    }

    public void addInventoryItem(final DataHeader dataHeader) {
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

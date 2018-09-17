package com.softwareverde.bitcoin.server.message.type.request;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

import java.util.ArrayList;
import java.util.List;

public class RequestDataMessage extends BitcoinProtocolMessage {
    public static final Integer MAX_COUNT = 50000;

    private final List<InventoryItem> _inventoryItems = new ArrayList<InventoryItem>();

    public RequestDataMessage() {
        super(MessageType.REQUEST_DATA);
    }

    public List<InventoryItem> getInventoryItems() {
        return Util.copyList(_inventoryItems);
    }

    public void addInventoryItem(final InventoryItem inventoryItem) {
        if (_inventoryItems.size() >= MAX_COUNT) { return; }
        _inventoryItems.add(inventoryItem);
    }

    public void clearInventoryItems() {
        _inventoryItems.clear();
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_inventoryItems.size()), Endian.BIG);
        for (final InventoryItem inventoryItem : _inventoryItems) {
            byteArrayBuilder.appendBytes(inventoryItem.getBytes(), Endian.BIG);
        }
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}

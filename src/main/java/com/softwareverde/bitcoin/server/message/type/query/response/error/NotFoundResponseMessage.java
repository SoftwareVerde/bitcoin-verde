package com.softwareverde.bitcoin.server.message.type.query.response.error;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class NotFoundResponseMessage extends BitcoinProtocolMessage {

    private final MutableList<InventoryItem> _inventoryItems = new MutableList<InventoryItem>();

    public NotFoundResponseMessage() {
        super(MessageType.NOT_FOUND);
    }

    public List<InventoryItem> getInventoryItems() {
        return _inventoryItems;
    }

    public void addItem(final InventoryItem inventoryItem) {
        _inventoryItems.add(inventoryItem);
    }

    public void clearItems() {
        _inventoryItems.clear();
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_inventoryItems.getCount()), Endian.BIG);
        for (final InventoryItem inventoryItem : _inventoryItems) {
            byteArrayBuilder.appendBytes(inventoryItem.getBytes(), Endian.BIG);
        }
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}

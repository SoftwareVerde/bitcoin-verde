package com.softwareverde.bitcoin.server.message.type.request;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class RequestDataMessage extends BitcoinProtocolMessage {
    public static final Integer MAX_COUNT = 50000;

    private final MutableList<InventoryItem> _inventoryItems = new MutableList<InventoryItem>();

    public RequestDataMessage() {
        super(MessageType.REQUEST_DATA);
    }

    public List<InventoryItem> getInventoryItems() {
        return _inventoryItems;
    }

    public void addInventoryItem(final InventoryItem inventoryItem) {
        if (_inventoryItems.getCount() >= MAX_COUNT) { return; }
        _inventoryItems.add(inventoryItem);
    }

    public void clearInventoryItems() {
        _inventoryItems.clear();
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_inventoryItems.getCount()), Endian.BIG);
        for (final InventoryItem inventoryItem : _inventoryItems) {
            byteArrayBuilder.appendBytes(inventoryItem.getBytes(), Endian.BIG);
        }
        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final byte[] itemCountBytes = ByteUtil.variableLengthIntegerToBytes(_inventoryItems.getCount());
        return (itemCountBytes.length + (_inventoryItems.getCount() * Sha256Hash.BYTE_COUNT));
    }
}

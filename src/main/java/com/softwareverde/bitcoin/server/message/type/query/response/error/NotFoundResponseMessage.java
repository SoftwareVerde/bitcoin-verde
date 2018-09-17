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

    private final MutableList<InventoryItem> _dataHashes = new MutableList<InventoryItem>();

    public NotFoundResponseMessage() {
        super(MessageType.NOT_FOUND);
    }

    public List<InventoryItem> getDataHashes() {
        return _dataHashes;
    }

    public void addItem(final InventoryItem inventoryItem) {
        _dataHashes.add(inventoryItem);
    }

    public void clearItems() {
        _dataHashes.clear();
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_dataHashes.getSize()), Endian.BIG);
        for (final InventoryItem inventoryItem : _dataHashes) {
            byteArrayBuilder.appendBytes(inventoryItem.getBytes(), Endian.BIG);
        }
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}

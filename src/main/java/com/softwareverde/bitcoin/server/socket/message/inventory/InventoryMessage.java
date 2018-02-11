package com.softwareverde.bitcoin.server.socket.message.inventory;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class InventoryMessage extends ProtocolMessage {

    public enum InventoryType {
        ERROR(0x00),
        TRANSACTION(0x01),
        BLOCK(0x02),
        FILTERED_BLOCK(0x03),
        COMPACT_BLOCK(0x04);

        public static InventoryType fromValue(final int value) {
            for (final InventoryType inventoryType : InventoryType.values()) {
                if (inventoryType._value == value) {
                    return inventoryType;
                }
            }
            return ERROR;
        }

        private final int _value;
        InventoryType(final int value) {
            _value = value;
        }

        public int getValue() {
            return _value;
        }
    }

    public static class InventoryItem {
        private final InventoryType _inventoryType;
        private byte[] _objectHash = new byte[32];

        public InventoryItem(final InventoryType inventoryType, final byte[] objectHash) {
            _inventoryType = inventoryType;
            ByteUtil.setBytes(_objectHash, objectHash);
        }

        public InventoryType getInventoryType() {
            return _inventoryType;
        }

        public byte[] getObjectHash() {
            return _objectHash;
        }

        public byte[] getBytes() {
            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

            final byte[] inventoryTypeBytes = new byte[4];
            ByteUtil.setBytes(inventoryTypeBytes, ByteUtil.integerToBytes(_inventoryType.getValue()));
            byteArrayBuilder.appendBytes(inventoryTypeBytes, Endian.LITTLE);
            byteArrayBuilder.appendBytes(_objectHash, Endian.LITTLE);
            return byteArrayBuilder.build();
        }
    }

    private final List<InventoryItem> _inventoryItems = new ArrayList<InventoryItem>();

    public InventoryMessage() {
        super(MessageType.INVENTORY);
    }

    public List<InventoryItem> getInventoryItems() {
        return Util.copyList(_inventoryItems);
    }

    public void addInventoryItem(final InventoryItem inventoryItem) {
        _inventoryItems.add(inventoryItem);
    }

    public void clearInventoryItems() {
        _inventoryItems.clear();
    }

    @Override
    protected byte[] _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_inventoryItems.size()), Endian.LITTLE);
        for (final InventoryItem inventoryItem : _inventoryItems) {
            byteArrayBuilder.appendBytes(inventoryItem.getBytes(), Endian.LITTLE);
        }
        return byteArrayBuilder.build();
    }
}

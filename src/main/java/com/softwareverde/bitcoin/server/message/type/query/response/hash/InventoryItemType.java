package com.softwareverde.bitcoin.server.message.type.query.response.hash;

public enum InventoryItemType {
    ERROR(0x00),
    TRANSACTION(0x01),
    BLOCK(0x02),
    FILTERED_BLOCK(0x03),
    COMPACT_BLOCK(0x04),
    EXTRA_THIN_BLOCK(0x05);

    public static InventoryItemType fromValue(final int value) {
        for (final InventoryItemType inventoryItemType : InventoryItemType.values()) {
            if (inventoryItemType._value == value) {
                return inventoryItemType;
            }
        }
        return ERROR;
    }

    private final int _value;
    InventoryItemType(final int value) {
        _value = value;
    }

    public int getValue() {
        return _value;
    }
}

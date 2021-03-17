package com.softwareverde.bitcoin.server.message.type.query.response.hash;

public enum InventoryItemType {
    ERROR                   (0x00000000),
    TRANSACTION             (0x00000001),
    BLOCK                   (0x00000002),
    MERKLE_BLOCK            (0x00000003),
    COMPACT_BLOCK           (0x00000004),
    EXTRA_THIN_BLOCK        (0x00000005),
    DOUBLE_SPEND_PROOF      (0x000094A0),
    // Custom Bitcoin Verde Types,
    VALID_SLP_TRANSACTION   (0x42560001),
    INVALID_SLP_TRANSACTION (0x42560002);

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

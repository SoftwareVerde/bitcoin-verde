package com.softwareverde.bitcoin.server.message.type.query.response.hash;

public enum InventoryItemType {
    ERROR                   (0x00000000),
    TRANSACTION             (0x00000001),
    BLOCK                   (0x00000002),
    MERKLE_BLOCK            (0x00000003),
    COMPACT_BLOCK           (0x00000004),
    EXTRA_THIN_BLOCK        (0x00000005),
    DOUBLE_SPEND_PROOF      (0x000094A0),

    // Utxo Commitment Types ("CMT")
    UTXO_COMMITMENT_ODD     (0x434D5402),
    UTXO_COMMITMENT_EVEN    (0x434D5403);

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

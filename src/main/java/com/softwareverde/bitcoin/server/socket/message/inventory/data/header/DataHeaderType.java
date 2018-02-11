package com.softwareverde.bitcoin.server.socket.message.inventory.data.header;

public enum DataHeaderType {
    ERROR(0x00),
    TRANSACTION(0x01),
    BLOCK(0x02),
    FILTERED_BLOCK(0x03),
    COMPACT_BLOCK(0x04);

    public static DataHeaderType fromValue(final int value) {
        for (final DataHeaderType dataHeaderType : DataHeaderType.values()) {
            if (dataHeaderType._value == value) {
                return dataHeaderType;
            }
        }
        return ERROR;
    }

    private final int _value;
    DataHeaderType(final int value) {
        _value = value;
    }

    public int getValue() {
        return _value;
    }
}

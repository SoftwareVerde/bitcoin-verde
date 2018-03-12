package com.softwareverde.bitcoin.server.message.type.query.response.hash;

public enum DataHashType {
    ERROR(0x00),
    TRANSACTION(0x01),
    BLOCK(0x02),
    FILTERED_BLOCK(0x03),
    COMPACT_BLOCK(0x04);

    public static DataHashType fromValue(final int value) {
        for (final DataHashType dataHashType : DataHashType.values()) {
            if (dataHashType._value == value) {
                return dataHashType;
            }
        }
        return ERROR;
    }

    private final int _value;
    DataHashType(final int value) {
        _value = value;
    }

    public int getValue() {
        return _value;
    }
}

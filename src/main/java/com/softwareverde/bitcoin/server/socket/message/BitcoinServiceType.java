package com.softwareverde.bitcoin.server.socket.message;

public enum BitcoinServiceType {
    NONE(0L), NETWORK(1L), UNCONFIRMED_TRANSACTION_OUTPUTS(2L), BLOOM(3L);

    private final Long _value;
    BitcoinServiceType(final Long value) {
        _value = value;
    }

    public Long getValue() {
        return _value;
    }
}

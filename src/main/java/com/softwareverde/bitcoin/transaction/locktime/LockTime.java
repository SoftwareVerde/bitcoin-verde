package com.softwareverde.bitcoin.transaction.locktime;

public interface LockTime {
    static final Long MAX_BLOCK_HEIGHT = 500_000_000L;
    static final Long MAX_TIMESTAMP = 0xFFFFFFFFL;

    Boolean isTimestamp();
    Long getBlockHeight();
    Long getTimestamp();
    byte[] getBytes();
}

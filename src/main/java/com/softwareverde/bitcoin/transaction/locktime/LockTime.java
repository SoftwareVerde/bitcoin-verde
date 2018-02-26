package com.softwareverde.bitcoin.transaction.locktime;

public interface LockTime {
    static final Long MAX_BLOCK_HEIGHT = 500_000_000L;
    static final Long MAX_TIMESTAMP = 0xFFFFFFFFL;
    static final Long MIN_TIMESTAMP = 0x00000000L;

    Boolean isTimestamp();
    Long getBlockHeight();
    Long getTimestamp();
    byte[] getBytes();
}

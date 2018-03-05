package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.constable.Constable;

public interface LockTime extends Constable<ImmutableLockTime> {
    static final Long MAX_BLOCK_HEIGHT = 500_000_000L;
    static final Long MAX_TIMESTAMP = 0xFFFFFFFFL;
    static final Long MIN_TIMESTAMP = 0x00000000L;

    enum Type {
        TIMESTAMP, BLOCK_HEIGHT
    }

    Type getType();
    Long getValue();
    byte[] getBytes();

    @Override
    ImmutableLockTime asConst();
}

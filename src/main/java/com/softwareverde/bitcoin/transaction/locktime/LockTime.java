package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.constable.Constable;

public interface LockTime extends Constable<ImmutableLockTime> {
    Long MAX_BLOCK_HEIGHT_VALUE = 500_000_000L;
    Long MAX_TIMESTAMP_VALUE = 0xFFFFFFFFL;
    Long MIN_TIMESTAMP_VALUE = 0x00000000L;

    LockTime MAX_BLOCK_HEIGHT = new ImmutableLockTime(MAX_BLOCK_HEIGHT_VALUE);
    LockTime MAX_TIMESTAMP = new ImmutableLockTime(LockTime.MAX_TIMESTAMP_VALUE);
    LockTime MIN_TIMESTAMP = new ImmutableLockTime(LockTime.MIN_TIMESTAMP_VALUE);

    enum Type {
        TIMESTAMP, BLOCK_HEIGHT
    }

    Type getType();
    Long getValue();
    byte[] getBytes();

    @Override
    ImmutableLockTime asConst();
}

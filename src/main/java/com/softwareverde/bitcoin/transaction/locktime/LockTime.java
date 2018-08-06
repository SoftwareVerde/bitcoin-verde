package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Jsonable;

public interface LockTime extends Constable<ImmutableLockTime>, Jsonable {
    Long MAX_BLOCK_HEIGHT_VALUE = 500_000_000L; // NOTE: This value is exclusive; therefore 500000000 is a timestamp value.
    Long MAX_TIMESTAMP_VALUE = 0xFFFFFFFFL;
    Long MIN_TIMESTAMP_VALUE = 0x00000000L;

    LockTime MAX_BLOCK_HEIGHT = new ImmutableLockTime(MAX_BLOCK_HEIGHT_VALUE);
    LockTime MAX_TIMESTAMP = new ImmutableLockTime(LockTime.MAX_TIMESTAMP_VALUE);
    LockTime MIN_TIMESTAMP = new ImmutableLockTime(LockTime.MIN_TIMESTAMP_VALUE);

    LockTimeType getType();
    Long getValue();
    ByteArray getBytes();

    @Override
    ImmutableLockTime asConst();
}

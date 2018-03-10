package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;

public class ImmutableLockTime implements LockTime, Const {
    private final Type _type;
    private final Long _lockTime;

    protected static Type _getType(final Long lockTime) {
        return ((lockTime < MAX_BLOCK_HEIGHT_VALUE) ? Type.TIMESTAMP : Type.BLOCK_HEIGHT);
    }

    public ImmutableLockTime() {
        _lockTime = MIN_TIMESTAMP_VALUE;
        _type = _getType(MIN_TIMESTAMP_VALUE);
    }

    public ImmutableLockTime(final Long value) {
        _lockTime = value;
        _type = _getType(value);
    }

    public ImmutableLockTime(final LockTime lockTime) {
        final Long value = lockTime.getValue();
        _lockTime = value;
        _type = _getType(value);
    }

    @Override
    public Type getType() {
        return _type;
    }

    @Override
    public Long getValue() {
        return _lockTime;
    }

    public byte[] getBytes() {
        // 4 Bytes...
        return ByteUtil.integerToBytes(_lockTime);
    }

    @Override
    public ImmutableLockTime asConst() {
        return this;
    }
}

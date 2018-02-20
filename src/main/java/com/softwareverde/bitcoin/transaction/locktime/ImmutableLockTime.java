package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.bitcoin.util.ByteUtil;

public class ImmutableLockTime implements LockTime {
    private final LockTime _lockTime;

    public ImmutableLockTime() {
        _lockTime = new MutableLockTime();
    }
    public ImmutableLockTime(final LockTime lockTime) {
        if (lockTime instanceof ImmutableLockTime) {
            _lockTime = lockTime;
            return;
        }

        _lockTime = new MutableLockTime(lockTime);
    }

    public Boolean isTimestamp() {
        return _lockTime.isTimestamp();
    }

    public Long getBlockHeight() {
        return _lockTime.getBlockHeight();
    }

    public Long getTimestamp() {
        return _lockTime.getTimestamp();
    }

    public byte[] getBytes() {
        return ByteUtil.copyBytes(_lockTime.getBytes());
    }
}

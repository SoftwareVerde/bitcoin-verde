package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.bitcoin.util.ByteUtil;

public class MutableLockTime implements LockTime {
    protected Long _lockTime = MAX_TIMESTAMP;

    public MutableLockTime() { }
    public MutableLockTime(final LockTime lockTime) {
        _lockTime = (lockTime.isTimestamp() ? lockTime.getTimestamp() : lockTime.getBlockHeight());
    }

    protected Boolean _isTimestamp() {
        return (_lockTime < MAX_BLOCK_HEIGHT);
    }

    public void setLockTime(final Long lockTime) {
        _lockTime = lockTime;
    }

    public void setLockTime(final byte[] lockTime) {
        _lockTime = ByteUtil.bytesToLong(lockTime);
    }

    @Override
    public Boolean isTimestamp() {
        return _isTimestamp();
    }

    @Override
    public Long getBlockHeight() {
        if (_isTimestamp()) { return null; }

        return _lockTime;
    }

    @Override
    public Long getTimestamp() {
        if (! _isTimestamp()) { return null; }

        return _lockTime;
    }

    @Override
    public byte[] getBytes() {
        return ByteUtil.integerToBytes(_lockTime.intValue());
    }
}

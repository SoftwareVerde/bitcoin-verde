package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.bitcoin.util.ByteUtil;

public class LockTime {
    public static final Long MAX_BLOCK_HEIGHT = 500_000_000L;
    public static final Long MAX_TIMESTAMP = 0xFFFFFFFFL;

    private Long _lockTime = MAX_TIMESTAMP;

    private Boolean _isTimestamp() {
        return (_lockTime < MAX_BLOCK_HEIGHT);
    }

    public LockTime() { }

    public void setLockTime(final Long lockTime) {
        _lockTime = lockTime;
    }

    public void setLockTime(final byte[] lockTime) {
        _lockTime = ByteUtil.bytesToLong(lockTime);
    }

    public Boolean isTimestamp() {
        return _isTimestamp();
    }

    public Long getBlockHeight() {
        if (_isTimestamp()) { return null; }

        return _lockTime;
    }

    public Long getTimestamp() {
        if (! _isTimestamp()) { return null; }

        return _lockTime;
    }

    public byte[] getBytes() {
        return ByteUtil.integerToBytes(_lockTime.intValue());
    }

    public LockTime copy() {
        final LockTime lockTime = new LockTime();
        lockTime._lockTime = _lockTime;
        return lockTime;
    }
}

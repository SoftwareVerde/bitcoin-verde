package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.util.DateUtil;

public class ImmutableLockTime implements LockTime, Const {
    protected final Long _value;

    protected static LockTimeType _getType(final Long lockTime) {
        return ((lockTime < MAX_BLOCK_HEIGHT_VALUE) ? LockTimeType.BLOCK_HEIGHT: LockTimeType.TIMESTAMP);
    }

    public ImmutableLockTime() {
        _value = MIN_TIMESTAMP_VALUE;
    }

    public ImmutableLockTime(final Long value) {
        _value = value;
    }

    public ImmutableLockTime(final LockTime lockTime) {
        final Long value = lockTime.getValue();
        _value = value;
    }

    @Override
    public LockTimeType getType() {
        return _getType(_value);
    }

    @Override
    public Long getValue() {
        return _value;
    }

    @Override
    public ByteArray getBytes() {
        // 4 Bytes...
        return MutableByteArray.wrap(ByteUtil.integerToBytes(_value));
    }

    @Override
    public ImmutableLockTime asConst() {
        return this;
    }

    @Override
    public Json toJson() {
        final LockTimeType type = _getType(_value);
        final Json json = new Json();
        json.put("type", type);
        json.put("date", (type == LockTimeType.TIMESTAMP ? DateUtil.Utc.timestampToDatetimeString(_value * 1000L) : null));
        json.put("value", _value);
        json.put("bytes", this.getBytes());
        return json;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof LockTime)) { return false; }

        final LockTime lockTime = (LockTime) object;
        return _value.equals(lockTime.getValue());
    }

    @Override
    public int hashCode() {
        return _value.hashCode();
    }

    @Override
    public String toString() {
        return _value.toString();
    }
}

package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;
import com.softwareverde.util.DateUtil;

public class ImmutableLockTime implements LockTime, Const {
    private final Long _value;

    protected static Type _getType(final Long lockTime) {
        return ((lockTime < MAX_BLOCK_HEIGHT_VALUE) ? Type.TIMESTAMP : Type.BLOCK_HEIGHT);
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
    public Type getType() {
        return _getType(_value);
    }

    @Override
    public Long getValue() {
        return _value;
    }

    @Override
    public Long getMaskedValue() {
        return (_value & 0x0000FFFF);
    }

    @Override
    public Boolean isDisabled() {
        return ((_value & 0x80000000) != 0);
    }

    public byte[] getBytes() {
        // 4 Bytes...
        return ByteUtil.integerToBytes(_value);
    }

    @Override
    public ImmutableLockTime asConst() {
        return this;
    }

    @Override
    public Json toJson() {
        final Type type = _getType(_value);
        final Json json = new Json();
        json.put("type", type);
        json.put("value", _value);
        json.put("date", (type == Type.TIMESTAMP ? DateUtil.Utc.timestampToDatetimeString(_value * 1000L) : null));
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

package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;
import com.softwareverde.util.DateUtil;

public class ImmutableLockTime implements LockTime, Const {
    private final Type _type;
    private final Long _value;

    protected static Type _getType(final Long lockTime) {
        return ((lockTime < MAX_BLOCK_HEIGHT_VALUE) ? Type.TIMESTAMP : Type.BLOCK_HEIGHT);
    }

    public ImmutableLockTime() {
        _value = MIN_TIMESTAMP_VALUE;
        _type = _getType(MIN_TIMESTAMP_VALUE);
    }

    public ImmutableLockTime(final Long value) {
        _value = value;
        _type = _getType(value);
    }

    public ImmutableLockTime(final LockTime lockTime) {
        final Long value = lockTime.getValue();
        _value = value;
        _type = _getType(value);
    }

    @Override
    public Type getType() {
        return _type;
    }

    @Override
    public Long getValue() {
        return _value;
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
        final Json json = new Json();
        json.put("type", _type);
        json.put("value", _value);
        json.put("date", (_type == Type.TIMESTAMP ? DateUtil.Utc.timestampToDatetimeString(_value * 1000L) : null));
        return json;
    }
}

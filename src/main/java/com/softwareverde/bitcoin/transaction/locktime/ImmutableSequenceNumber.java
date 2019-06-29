package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Json;

public class ImmutableSequenceNumber implements SequenceNumber, Const {
    protected final Long _value;

    protected Long _getMaskedValue() {
        return (_value & 0x0000FFFF);
    }

    protected SequenceNumberType _getType() {
        final Boolean isTimeSpan = ( (_value & (1 << 22)) != 0x00);
        return (isTimeSpan ? SequenceNumberType.SECONDS_ELAPSED : SequenceNumberType.BLOCK_COUNT);
    }

    public ImmutableSequenceNumber(final Long value) {
        _value = value;
    }

    public ImmutableSequenceNumber(final SequenceNumber sequenceNumber) {
        _value = sequenceNumber.getValue();
    }

    @Override
    public Long getValue() {
        return _value;
    }

    @Override
    public SequenceNumberType getType() {
        return _getType();
    }

    @Override
    public Long getMaskedValue() {
        return _getMaskedValue();
    }

    @Override
    public Long asSecondsElapsed() {
        final Long maskedValue = _getMaskedValue();
        return (maskedValue * SECONDS_PER_SEQUENCE_NUMBER);
    }

    @Override
    public Long asBlockCount() {
        return _getMaskedValue();
    }

    @Override
    public Boolean isDisabled() {
        return ((_value & 0x80000000L) != 0L);
    }

    @Override
    public ByteArray getBytes() {
        // 4 Bytes...
        return MutableByteArray.wrap(ByteUtil.integerToBytes(_value));
    }

    @Override
    public ImmutableSequenceNumber asConst() {
        return this;
    }

    @Override
    public Json toJson() {
        final SequenceNumberType type = _getType();

        final Json json = new Json();
        json.put("type", type);
        json.put("isDisabled", (this.isDisabled() ? 1 : 0));
        json.put("value", (type == SequenceNumberType.SECONDS_ELAPSED ?  this.asSecondsElapsed() : this.asBlockCount()));
        json.put("bytes", this.getBytes());
        return json;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof SequenceNumber)) { return false; }

        final SequenceNumber sequenceNumber = (SequenceNumber) object;
        return _value.equals(sequenceNumber.getValue());
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

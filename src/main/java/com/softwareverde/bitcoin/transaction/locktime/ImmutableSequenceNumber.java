package com.softwareverde.bitcoin.transaction.locktime;

public class ImmutableSequenceNumber extends ImmutableLockTime implements SequenceNumber {
    protected Long _getMaskedValue() {
        return (_value & 0x0000FFFF);
    }

    public ImmutableSequenceNumber() { }

    public ImmutableSequenceNumber(final Long value) {
        super(value);
    }

    public ImmutableSequenceNumber(final LockTime lockTime) {
        super(lockTime);
    }

    @Override
    public Type getType() {
        final Boolean isTimeSpan = ( (_value & (1 << 22)) != 0x00);
        return (isTimeSpan ? Type.TIMESTAMP : Type.BLOCK_HEIGHT);
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
        return ((_value & 0x80000000) != 0);
    }

    @Override
    public ImmutableSequenceNumber asConst() {
        return this;
    }
}

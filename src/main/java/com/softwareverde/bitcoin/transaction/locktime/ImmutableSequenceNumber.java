package com.softwareverde.bitcoin.transaction.locktime;

public class ImmutableSequenceNumber extends ImmutableLockTime implements SequenceNumber {
    public ImmutableSequenceNumber() { }

    public ImmutableSequenceNumber(final Long value) {
        super(value);
    }

    public ImmutableSequenceNumber(final LockTime lockTime) {
        super(lockTime);
    }

    @Override
    public ImmutableSequenceNumber asConst() {
        return this;
    }
}

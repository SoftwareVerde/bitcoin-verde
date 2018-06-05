package com.softwareverde.bitcoin.transaction.locktime;

public interface SequenceNumber extends LockTime {
    SequenceNumber MAX_SEQUENCE_NUMBER = new ImmutableSequenceNumber(LockTime.MAX_TIMESTAMP_VALUE);
    SequenceNumber EMPTY_SEQUENCE_NUMBER = new ImmutableSequenceNumber(LockTime.MIN_TIMESTAMP_VALUE);

    @Override
    ImmutableSequenceNumber asConst();
}

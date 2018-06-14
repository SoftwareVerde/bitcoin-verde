package com.softwareverde.bitcoin.transaction.locktime;

public interface SequenceNumber extends LockTime {
    SequenceNumber MAX_SEQUENCE_NUMBER = new ImmutableSequenceNumber(LockTime.MAX_TIMESTAMP_VALUE);
    SequenceNumber EMPTY_SEQUENCE_NUMBER = new ImmutableSequenceNumber(LockTime.MIN_TIMESTAMP_VALUE);

    Long getMaskedValue(); // Returns the last 2 bytes of the value, as per Bip68... (https://github.com/bitcoin/bips/blob/master/bip-0068.mediawiki)
    Boolean isDisabled();

    @Override
    ImmutableSequenceNumber asConst();
}

package com.softwareverde.bitcoin.transaction.locktime;

import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Jsonable;

public interface SequenceNumber extends Constable<ImmutableSequenceNumber>, Jsonable {
    Long SECONDS_PER_SEQUENCE_NUMBER = 512L;

    SequenceNumber MAX_SEQUENCE_NUMBER = new ImmutableSequenceNumber(LockTime.MAX_TIMESTAMP_VALUE);
    SequenceNumber EMPTY_SEQUENCE_NUMBER = new ImmutableSequenceNumber(LockTime.MIN_TIMESTAMP_VALUE);

    SequenceNumberType getType();
    Long getValue();
    ByteArray getBytes();

    Boolean isDisabled();

    Long getMaskedValue(); // Returns the last 2 bytes of the value, as per Bip68... (https://github.com/bitcoin/bips/blob/master/bip-0068.mediawiki)

    Long asSecondsElapsed(); // Returns the masked value transformed to the number of seconds the input is required to be locked...
    Long asBlockCount(); // Returns the masked value transformed to the number of blocks the input is required to be locked...

    @Override
    ImmutableSequenceNumber asConst();
}

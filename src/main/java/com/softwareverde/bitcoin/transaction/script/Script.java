package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.type.bytearray.ByteArray;

public interface Script extends ByteArray {
    byte peakNextByte();

    byte getNextByte();
    byte[] getNextBytes(final Integer byteCount);
    Boolean hasNextByte();
    void resetPosition();

    int getByteCount();
}

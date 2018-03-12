package com.softwareverde.bitcoin.type.bytearray;

import com.softwareverde.constable.Constable;

public interface ByteArray extends Constable<ImmutableByteArray> {

    byte getByte(int index);
    byte[] getBytes(int index, int byteCount);
    byte[] getBytes();

    int getByteCount();
    boolean isEmpty();

    @Override
    ImmutableByteArray asConst();
}

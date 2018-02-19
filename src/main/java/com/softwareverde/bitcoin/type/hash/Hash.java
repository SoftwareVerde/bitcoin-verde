package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.bitcoin.type.bytearray.ByteArray;

public interface Hash extends ByteArray {
    static final Integer BYTE_COUNT = 32;

    byte get(int index);
}

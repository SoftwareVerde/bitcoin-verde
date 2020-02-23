package com.softwareverde.bitcoin.util;

public class ByteBuffer extends com.softwareverde.util.ByteBuffer {
    public void recycleBuffer(final byte[] buffer) {
        _recycledByteArrays.addLast(new Buffer(buffer, 0, 0));
    }
}

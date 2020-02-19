package com.softwareverde.bitcoin.util;

public class ByteBuffer extends com.softwareverde.util.ByteBuffer {
    public void returnRecycledBuffer(final byte[] buffer) {
        super.appendBytes(buffer, 0);
    }
}

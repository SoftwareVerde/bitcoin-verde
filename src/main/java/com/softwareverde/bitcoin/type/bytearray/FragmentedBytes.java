package com.softwareverde.bitcoin.type.bytearray;

public class FragmentedBytes {
    public final byte[] headBytes;
    public final byte[] tailBytes;

    public FragmentedBytes(final byte[] headBytes, final byte[] tailBytes) {
        this.headBytes = headBytes;
        this.tailBytes = tailBytes;
    }
}

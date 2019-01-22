package com.softwareverde.bitcoin.bytearray;

public class FragmentedBytes {
    public final byte[] headBytes;
    public final byte[] tailBytes;

    public FragmentedBytes(final byte[] headBytes, final byte[] tailBytes) {
        this.headBytes = headBytes;
        this.tailBytes = tailBytes;
    }
}

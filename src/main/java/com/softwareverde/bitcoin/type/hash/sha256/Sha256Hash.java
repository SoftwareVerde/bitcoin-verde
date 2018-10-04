package com.softwareverde.bitcoin.type.hash.sha256;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

public interface Sha256Hash extends Hash {
    static Sha256Hash fromHexString(final String hexString) {
        if (hexString == null) { return null; }

        final byte[] hashBytes = HexUtil.hexStringToByteArray(hexString);
        if (hashBytes == null) {
            Logger.log("NOTICE: Unable to parse hash from string. Invalid hex string: "+ hexString);
            return null;
        }
        if (hashBytes.length != BYTE_COUNT) { return null; }

        return new ImmutableSha256Hash(hashBytes);
    }

    static Sha256Hash copyOf(final byte[] bytes) {
        return new ImmutableSha256Hash(bytes);
    }

    Integer BYTE_COUNT = 32;
    ImmutableSha256Hash EMPTY_HASH = new ImmutableSha256Hash();

    @Override
    public Sha256Hash toReversedEndian();

    @Override
    ImmutableSha256Hash asConst();
}

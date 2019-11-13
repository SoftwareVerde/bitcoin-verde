package com.softwareverde.bitcoin.slp;

import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;

import java.util.Comparator;

public class SlpTokenId extends ImmutableSha256Hash {
    public static final Comparator<SlpTokenId> COMPARATOR = new Comparator<SlpTokenId>() {
        @Override
        public int compare(final SlpTokenId slpTokenId0, final SlpTokenId slpTokenId1) {
            return Sha256Hash.COMPARATOR.compare(slpTokenId0, slpTokenId1);
        }
    };

    public static SlpTokenId wrap(final Sha256Hash value) {
        if (value == null) { return null; }
        return new SlpTokenId(value);
    }

    public static SlpTokenId copyOf(final byte[] value) {
        final Sha256Hash sha256Hash = Sha256Hash.copyOf(value);
        if (sha256Hash == null) { return null; }
        return new SlpTokenId(sha256Hash);
    }

    public static SlpTokenId fromHexString(final String hexString) {
        final Sha256Hash sha256Hash = Sha256Hash.fromHexString(hexString);
        if (sha256Hash == null) { return null; }
        return new SlpTokenId(sha256Hash);
    }

    protected SlpTokenId(final Sha256Hash value) {
        super(value);
    }

    @Override
    public SlpTokenId asConst() {
        return this;
    }
}

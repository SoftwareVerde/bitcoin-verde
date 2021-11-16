package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.Util;

public class MultisetBucket {
    protected final PublicKey _multisetPublicKey;
    protected final Long _byteCount;

    public MultisetBucket(final PublicKey multisetPublicKey, final Long byteCount) {
        _multisetPublicKey = multisetPublicKey;
        _byteCount = byteCount;
    }

    public PublicKey getPublicKey() {
        return _multisetPublicKey;
    }

    public Long getByteCount() {
        return _byteCount;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }
        if (! (object instanceof MultisetBucket)) { return false; }

        final MultisetBucket multisetBucket = (MultisetBucket) object;
        if (! Util.areEqual(_multisetPublicKey, multisetBucket._multisetPublicKey)) { return false; }
        if (! Util.areEqual(_byteCount, multisetBucket._byteCount)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return _multisetPublicKey.hashCode();
    }
}

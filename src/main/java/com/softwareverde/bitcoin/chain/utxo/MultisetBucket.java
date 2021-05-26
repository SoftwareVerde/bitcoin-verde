package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class MultisetBucket {
    protected final Sha256Hash _multisetHash;
    protected final Long _byteCount;

    public MultisetBucket(final Sha256Hash multisetHash, final Long byteCount) {
        _multisetHash = multisetHash;
        _byteCount = byteCount;
    }

    public Sha256Hash getHash() {
        return _multisetHash;
    }

    public Long getByteCount() {
        return _byteCount;
    }
}

package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface Hashable {
    Sha256Hash getHash();
}

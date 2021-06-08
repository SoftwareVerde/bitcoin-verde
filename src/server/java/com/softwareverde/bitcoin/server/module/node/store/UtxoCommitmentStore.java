package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;

public interface UtxoCommitmentStore {
    Boolean storeUtxoCommitment(PublicKey publicKey, ByteArray utxoCommitment);
    void removeUtxoCommitment(PublicKey publicKey);
    ByteArray getUtxoCommitment(PublicKey publicKey);
    Boolean blockExists(PublicKey publicKey);
    String getDataDirectory();
}

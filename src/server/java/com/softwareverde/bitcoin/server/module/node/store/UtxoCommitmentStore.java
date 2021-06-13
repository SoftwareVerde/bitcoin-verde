package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;

import java.io.File;

public interface UtxoCommitmentStore {
    File storeUtxoCommitment(PublicKey publicKey, ByteArray utxoCommitment);
    void removeUtxoCommitment(PublicKey publicKey);
    File getUtxoCommitmentFile(PublicKey publicKey);
    ByteArray getUtxoCommitment(PublicKey publicKey);
    Boolean utxoCommitmentExists(PublicKey publicKey);
    String getDataDirectory();
    String getUtxoDataDirectory();
}

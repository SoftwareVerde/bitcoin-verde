package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentBreakdown;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;

public interface UtxoCommitmentManager {
    List<UtxoCommitmentBreakdown> getAvailableUtxoCommitments();
    ByteArray getUtxoCommitment(PublicKey utxoCommitmentFile);
}

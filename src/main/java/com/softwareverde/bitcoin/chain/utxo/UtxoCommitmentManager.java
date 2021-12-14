package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.message.type.query.utxo.NodeSpecificUtxoCommitmentBreakdown;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.database.DatabaseException;

public interface UtxoCommitmentManager {
    List<NodeSpecificUtxoCommitmentBreakdown> getAvailableUtxoCommitments() throws DatabaseException;
    ByteArray getUtxoCommitment(PublicKey utxoCommitmentFile) throws DatabaseException;
    UtxoCommitmentId getUtxoCommitmentId(BlockId blockId) throws DatabaseException;
}

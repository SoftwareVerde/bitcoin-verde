package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.Constable;
import com.softwareverde.json.Jsonable;

public interface BlockHeader extends Hashable, Constable<ImmutableBlockHeader>, Jsonable {
    Long VERSION = BitcoinConstants.getBlockVersion();
    Sha256Hash GENESIS_BLOCK_HASH = Sha256Hash.fromHexString(BitcoinConstants.getGenesisBlockHash());

    static Long calculateBlockReward(final Long blockHeight) {
        return ((50 * Transaction.SATOSHIS_PER_BITCOIN) >> (blockHeight / 210000));
    }

    Sha256Hash getPreviousBlockHash();
    MerkleRoot getMerkleRoot();
    Difficulty getDifficulty();
    Long getVersion();
    Long getTimestamp();
    Long getNonce();

    Boolean isValid();

    @Override
    ImmutableBlockHeader asConst();
}

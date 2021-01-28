package com.softwareverde.bitcoin.server.stratum.task;

import com.softwareverde.constable.list.List;

/**
 * Extended StratumMineBlockTaskBuilder interface for proxying stratum functions from external servers (e.g. ViaBTC).
 */
public interface RelayedStratumMineBlockTaskBuilder extends StratumMineBlockTaskBuilder {
    void setBlockVersion(String stratumBlockVersion);
    void setPreviousBlockHash(String stratumPreviousBlockHash);
    void setExtraNonce(String stratumExtraNonce);
    void setDifficulty(String stratumDifficulty);
    void setMerkleTreeBranches(List<String> merkleTreeBranches); // ViaBTC provides the merkleTreeBranches as little-endian byte strings.
    void setCoinbaseTransaction(String stratumCoinbaseTransactionHead, String stratumCoinbaseTransactionTail);
}

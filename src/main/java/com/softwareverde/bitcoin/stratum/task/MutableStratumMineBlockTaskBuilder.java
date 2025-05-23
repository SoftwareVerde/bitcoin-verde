package com.softwareverde.bitcoin.stratum.task;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface MutableStratumMineBlockTaskBuilder extends StratumMineBlockTaskBuilder {
    void setBlockVersion(Long blockVersion);
    void setPreviousBlockHash(Sha256Hash previousBlockHash);
    void setExtraNonce(ByteArray extraNonce);
    void setDifficulty(Difficulty difficulty);
    void setCoinbaseTransaction(Transaction coinbaseTransaction);
    void setBlockHeight(Long blockHeight);
    void setTransactions(List<Transaction> transactions);

    Long getBlockHeight();
    CoinbaseTransaction getCoinbaseTransaction();
}

package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.constable.list.List;

public interface Block extends BlockHeaderWithTransactionCount {
    List<Transaction> getTransactions();
    CoinbaseTransaction getCoinbaseTransaction();
    List<Sha256Hash> getPartialMerkleTree(int transactionIndex);
    Boolean hasTransaction(Transaction transaction);

    @Override
    ImmutableBlock asConst();
}

package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.list.List;

public interface Block extends BlockHeader {
    List<Transaction> getTransactions();
    Transaction getCoinbaseTransaction();
    List<Hash> getPartialMerkleTree(int transactionIndex);

    @Override
    ImmutableBlock asConst();
}

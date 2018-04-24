package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;

public interface Block extends BlockHeader {
    List<Transaction> getTransactions();
    Transaction getCoinbaseTransaction();
    List<Sha256Hash> getPartialMerkleTree(int transactionIndex);

    @Override
    ImmutableBlock asConst();
}

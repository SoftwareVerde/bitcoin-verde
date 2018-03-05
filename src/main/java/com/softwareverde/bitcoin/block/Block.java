package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;

public interface Block extends BlockHeader {
    List<Transaction> getTransactions();

    @Override
    ImmutableBlock asConst();
}

package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.transaction.Transaction;

import java.util.List;

public interface Block extends BlockHeader {
    List<Transaction> getTransactions();
}

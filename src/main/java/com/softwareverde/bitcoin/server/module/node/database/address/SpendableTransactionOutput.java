package com.softwareverde.bitcoin.server.module.node.database.address;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;

public interface SpendableTransactionOutput {
    BlockId getBlockId();
    TransactionId getTransactionId();
    TransactionOutputId getTransactionOutputId();
    Long getAmount();
    TransactionInputId getSpentByTransactionInputId();

    Boolean wasSpent();
    Boolean isMined();
    Boolean isUnconfirmed();
}

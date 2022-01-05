package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;

public interface UnspentTransactionOutputVisitor {
    void run(TransactionOutputIdentifier transactionOutputIdentifier, UnspentTransactionOutput transactionOutput) throws Exception;
}

package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;

public interface UnspentTransactionOutputSetFactory {
    UnspentTransactionOutputSet newUnspentTransactionOutputSet(FullNodeDatabaseManager databaseManager);
}

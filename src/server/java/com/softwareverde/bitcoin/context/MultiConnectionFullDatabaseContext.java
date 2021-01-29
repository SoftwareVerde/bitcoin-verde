package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;

public interface MultiConnectionFullDatabaseContext extends MultiConnectionDatabaseContext {
    @Override
    FullNodeDatabaseManagerFactory getDatabaseManagerFactory();
}

package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;

public interface MultiConnectionDatabaseContext {
    DatabaseManagerFactory getDatabaseManagerFactory();
}

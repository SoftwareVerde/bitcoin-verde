package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;

public interface FullDatabaseContext extends DatabaseContext {
    @Override
    FullNodeDatabaseManager getDatabaseManager();
}

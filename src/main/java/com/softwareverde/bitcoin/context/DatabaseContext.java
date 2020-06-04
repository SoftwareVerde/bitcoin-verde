package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.server.module.node.database.*;

public interface DatabaseContext {
    DatabaseManager getDatabaseManager();
}

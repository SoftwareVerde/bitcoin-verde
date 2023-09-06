package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactoryFactory;

public class Environment {

    protected final ExplorerProperties _explorerProperties;

    public Environment(final ExplorerProperties explorerProperties) {
        _explorerProperties = explorerProperties;
    }

    public ExplorerProperties getExplorerProperties() {
        return _explorerProperties;
    }
}
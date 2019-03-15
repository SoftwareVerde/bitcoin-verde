package com.softwareverde.bitcoin.server.database.impl;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public class DatabaseConnectionImpl extends DatabaseConnection {
    public DatabaseConnectionImpl(final MysqlDatabaseConnection core) {
        super(core);
    }

    @Override
    public Integer getRowsAffectedCount() {
        return ((MysqlDatabaseConnection) _core).getRowsAffectedCount();
    }
}

package com.softwareverde.bitcoin.server.database.wrapper;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public class DatabaseConnectionWrapper extends DatabaseConnection {
    public DatabaseConnectionWrapper(final MysqlDatabaseConnection core) {
        super(core);
    }

    @Override
    public Integer getRowsAffectedCount() {
        return ((MysqlDatabaseConnection) _core).getRowsAffectedCount();
    }
}

package com.softwareverde.database.mysql.embedded.factory;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public interface DatabaseConnectionFactory {
    MysqlDatabaseConnection newConnection() throws DatabaseException;
}

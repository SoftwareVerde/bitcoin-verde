package com.softwareverde.bitcoin.server.database;

import java.sql.Connection;

public interface DatabaseConnection extends com.softwareverde.database.DatabaseConnection<Connection> {
    Integer getRowsAffectedCount();
}

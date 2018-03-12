package com.softwareverde.database.mysql.embedded;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.vorburger.DB;

import java.util.List;

public class DatabaseInitializer {
    public void initializeDatabase(final DB databaseInstance, final DatabaseConnectionFactory databaseConnectionFactory, final Credentials maintenanceCredentials) throws DatabaseException {
        final Integer databaseVersionNumber;
        {
            Integer versionNumber = 0;
            try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                final List<Row> rows = databaseConnection.query("SELECT version FROM metadata ORDER BY id DESC LIMIT 1", null);
                if (! rows.isEmpty()) {
                    final Row row = rows.get(0);
                    versionNumber = row.getInteger("version");
                }
            }
            catch (final Exception exception) { }
            databaseVersionNumber = versionNumber;
        }

        try {
            if (databaseVersionNumber < 1) {
                databaseInstance.source("queries/metadata_init.sql", maintenanceCredentials.username, maintenanceCredentials.password, maintenanceCredentials.schema);
                databaseInstance.source("queries/init.sql", maintenanceCredentials.username, maintenanceCredentials.password, maintenanceCredentials.schema);
            }
            else if (databaseVersionNumber < Constants.DATABASE_VERSION) {
                // TODO: Handle upgrades...
                throw new RuntimeException("Database upgrades not supported yet.");
            }
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }
}

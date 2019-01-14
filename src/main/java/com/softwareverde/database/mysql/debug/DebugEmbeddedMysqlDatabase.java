package com.softwareverde.database.mysql.debug;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;

public class DebugEmbeddedMysqlDatabase extends EmbeddedMysqlDatabase {
    public DebugEmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer databaseInitializer, final DatabaseCommandLineArguments databaseCommandLineArguments) throws DatabaseException {
        super(databaseProperties, databaseInitializer, databaseCommandLineArguments);
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        return new LoggingConnectionWrapper(super.newConnection());
    }

    @Override
    public MysqlDatabaseConnectionFactory newConnectionFactory() {
        return new LoggingConnectionFactoryWrapper(super.newConnectionFactory());
    }
}

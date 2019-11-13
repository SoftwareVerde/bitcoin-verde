package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.database.row.Row;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class DatabaseConnectionPoolTests extends IntegrationTest {
    protected Integer _getCurrentConnectionCount(final DatabaseConnection databaseConnection) throws Exception {
        final List<Row> rows = databaseConnection.query(new Query("SHOW STATUS WHERE variable_name = 'Threads_connected'"));
        final Row row = rows.get(0);
        return row.getInteger("value");
    }

    @Before
    public void setUp() {
        _resetDatabase();
    }

    @Test
    public void should_not_surpass_max_connection_count() throws Exception {
        // Setup
        final DatabaseConnection databaseConnection = _database.newConnection();
        final Integer baselineConnectionCount = _getCurrentConnectionCount(databaseConnection);

        final Integer maxConnectionCount = 10;
        final DatabaseConnectionPool databaseConnectionPool = _database.getDatabaseConnectionPool();

        final DatabaseConnection[] pooledConnections = new DatabaseConnection[maxConnectionCount];

        // Action
        for (int i = 0; i < maxConnectionCount; ++i) {
            final DatabaseConnection pooledConnection = databaseConnectionPool.newConnection();
            pooledConnections[i] = pooledConnection;

            // Assert
            final Integer newConnectionCount = _getCurrentConnectionCount(pooledConnection);
            final Integer expectedConnectionCount = (Math.min(i + 1, maxConnectionCount) + baselineConnectionCount);
            Assert.assertEquals(expectedConnectionCount, newConnectionCount);
        }

        // Should not actually close any connections... just mark them as available.
        for (final DatabaseConnection pooledDatabaseConnection : pooledConnections) {
            pooledDatabaseConnection.close();
        }

        { // Assert
            final Integer newConnectionCount = _getCurrentConnectionCount(databaseConnection);
            final Integer expectedConnectionCount = (maxConnectionCount + baselineConnectionCount);
            Assert.assertEquals(expectedConnectionCount, newConnectionCount);
        }

        // Action
        for (int i = 0; i < maxConnectionCount * 2; ++i) {
            try (final DatabaseConnection pooledConnection = databaseConnectionPool.newConnection()) {
                // Assert
                final Integer newConnectionCount = _getCurrentConnectionCount(pooledConnection);
                final Integer expectedConnectionCount = (maxConnectionCount + baselineConnectionCount);
                Assert.assertEquals(expectedConnectionCount, newConnectionCount);
            }
        }

        databaseConnectionPool.close();

        // Assert
        final Integer newConnectionCount = _getCurrentConnectionCount(databaseConnection);
        Assert.assertEquals(baselineConnectionCount, newConnectionCount);
        Assert.assertEquals(0, databaseConnectionPool.getAliveConnectionCount().intValue());
    }
}

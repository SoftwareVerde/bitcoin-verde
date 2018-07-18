package com.softwareverde.database.mysql.embedded.factory;

import com.softwareverde.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReadUncommittedDatabaseConnectionFactory implements DatabaseConnectionFactory<Connection> {
    protected final MysqlDatabaseConnectionFactory _mysqlDatabaseConnectionFactory;

    public ReadUncommittedDatabaseConnectionFactory(final MysqlDatabaseConnectionFactory mysqlDatabaseConnectionFactory) {
        _mysqlDatabaseConnectionFactory = mysqlDatabaseConnectionFactory;
    }

    public static class Q extends MysqlDatabaseConnection {

        public Q(final MysqlDatabaseConnection connection) {
            super(connection.getRawConnection());
        }

        public Q(final Connection connection) {
            super(connection);
        }

        protected static final Object mutex = new Object();
        protected static final Map<String, Long> queryValues = new HashMap<String, Long>();
        protected static final Map<String, Long> queryCounts = new HashMap<String, Long>();
        protected static long queryCount = 0L;

        protected void _log(final Query query, final long duration) {
            synchronized (mutex) {
                final String queryString = query.getQueryString();
                final Long currentValue = Util.coalesce(queryValues.get(queryString), 0L);
                queryValues.put(queryString, currentValue + duration);

                final Long currentCount = Util.coalesce(queryCounts.get(queryString), 0L);
                queryCounts.put(queryString, currentCount + 1);
            }

            synchronized (mutex) {
                if ( (queryCount % 100000L) == 0L ) {
                    Logger.log("");
                    for (final String queryString : queryValues.keySet()) {
                        final Long qsDuration = queryValues.get(queryString);
                        final Long qsCount = queryCounts.get(queryString);
                        Logger.log(qsDuration + " - " + qsCount + " (" + (qsDuration.floatValue() / qsCount.floatValue()) + ") " + queryString);
                    }
                    Logger.log("");
                }

                queryCount += 1L;
            }
        }

        @Override
        public List<Row> query(final Query query) throws DatabaseException {
            final long startTime = System.currentTimeMillis();
            final List<Row> rows = super.query(query);
            final long endTime = System.currentTimeMillis();
            final long duration = (endTime - startTime);

            _log(query, duration);

            return rows;
        }

        @Override
        public synchronized Long executeSql(final Query query) throws DatabaseException {
            final long startTime = System.currentTimeMillis();
            final Long rowId = super.executeSql(query);
            final long endTime = System.currentTimeMillis();
            final long duration = (endTime - startTime);

            _log(query, duration);

            return rowId;
        }
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        final MysqlDatabaseConnection mysqlDatabaseConnection = new Q(_mysqlDatabaseConnectionFactory.newConnection());
        mysqlDatabaseConnection.executeSql("SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED", null);
        return mysqlDatabaseConnection;
    }
}

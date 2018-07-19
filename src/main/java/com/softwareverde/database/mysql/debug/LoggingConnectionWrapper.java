package com.softwareverde.database.mysql.debug;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.Timer;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoggingConnectionWrapper extends MysqlDatabaseConnection {

    public LoggingConnectionWrapper(final MysqlDatabaseConnection connection) {
        super(connection.getRawConnection());
    }

    public LoggingConnectionWrapper(final Connection connection) {
        super(connection);
    }

    protected static final Object mutex = new Object();
    protected static final Map<String, Double> queryValues = new HashMap<String, Double>();
    protected static final Map<String, Long> queryCounts = new HashMap<String, Long>();
    protected static long queryCount = 0L;

    protected void _log(final Query query, final Double duration) {
        synchronized (mutex) {
            final String queryString = query.getQueryString();
            final Double currentValue = Util.coalesce(queryValues.get(queryString), 0D);
            queryValues.put(queryString, currentValue + duration);

            final Long currentCount = Util.coalesce(queryCounts.get(queryString), 0L);
            queryCounts.put(queryString, currentCount + 1);
        }

        synchronized (mutex) {
            if ( (queryCount % 100000L) == 0L ) {
                Logger.log("");
                for (final String queryString : queryValues.keySet()) {
                    final Double qsDuration = queryValues.get(queryString);
                    final Long qsCount = queryCounts.get(queryString);
                    Logger.log(String.format("%.2f", qsDuration) + " - " + qsCount + " (" + (qsDuration / qsCount.floatValue()) + ") " + queryString);
                }
                Logger.log("");
            }

            queryCount += 1L;
        }
    }

    @Override
    public List<Row> query(final Query query) throws DatabaseException {
        final Timer timer = new Timer();
        timer.start();
        final List<Row> rows = super.query(query);
        timer.stop();

        _log(query, timer.getMillisecondsElapsed());

        return rows;
    }

    @Override
    public synchronized Long executeSql(final Query query) throws DatabaseException {
        final Timer timer = new Timer();
        timer.start();
        final Long rowId = super.executeSql(query);
        timer.stop();

        _log(query, timer.getMillisecondsElapsed());

        return rowId;
    }
}

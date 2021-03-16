package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleDatabaseConnectionPool implements DatabaseConnectionPool {
    protected final Integer _queueSize;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final AtomicInteger _connectionCount = new AtomicInteger(0);
    protected final ConcurrentLinkedQueue<DatabaseConnection> _databaseConnections = new ConcurrentLinkedQueue<>();

    protected final Thread _thread;
    protected final Query _testQuery = new Query("SELECT 1");

    protected Boolean _checkDatabaseConnection(final DatabaseConnection databaseConnection) {
        try {
            final java.util.List<Row> rows = databaseConnection.query(_testQuery);
            return (! rows.isEmpty());
        }
        catch (final Exception exception) {
            return false;
        }
    }

    public SimpleDatabaseConnectionPool(final DatabaseConnectionFactory databaseConnectionFactory, final Integer queueSize) {
        _queueSize = queueSize;
        _databaseConnectionFactory = databaseConnectionFactory;

        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        try {
                            int connectionCount = _connectionCount.get();
                            while (connectionCount < _queueSize) {
                                final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
                                _databaseConnections.add(databaseConnection);
                                connectionCount = _connectionCount.incrementAndGet();
                            }
                        }
                        catch (final DatabaseException exception) {
                            Logger.warn(exception);
                        }

                        synchronized (_databaseConnections) {
                            _databaseConnections.wait();
                        }
                    }
                }
                catch (final InterruptedException exception) { }
            }
        });
        _thread.setName("SimpleDatabaseConnectionPool Maintainer");
        _thread.setDaemon(false);
        _thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.error(exception);
            }
        });

        _thread.start();
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        try {
            int attemptCount = 0;
            final int maxAttemptCount = _queueSize;
            do {
                final DatabaseConnection databaseConnection = _databaseConnections.poll();
                if (databaseConnection == null) { break; }

                _connectionCount.decrementAndGet();

                final Boolean isConnectionAlive = _checkDatabaseConnection(databaseConnection);
                if (isConnectionAlive) {
                    Logger.trace("Using cached db connection.");
                    return databaseConnection;
                }

                attemptCount += 1;
            } while (attemptCount < maxAttemptCount);

            if (Logger.isTraceEnabled()) {
                Logger.trace("Creating new db connection after " + attemptCount + " cached connection lookups.");
            }

            return _databaseConnectionFactory.newConnection();
        }
        finally {
            synchronized (_databaseConnections) {
                _databaseConnections.notifyAll();
            }
        }
    }

    @Override
    public void close() throws DatabaseException {
        _thread.interrupt();

        try {
            _thread.join();
        }
        catch (final InterruptedException exception) {
            final Thread thread = Thread.currentThread();
            thread.interrupt();
        }
    }
}

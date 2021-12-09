package com.softwareverde.bitcoin.server.properties;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.query.Query;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Tuple;

import java.util.concurrent.ConcurrentHashMap;

public class DatabasePropertiesStore implements PropertiesStore {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final Thread _thread;
    protected final Object _mutex = new Object();
    protected final ConcurrentHashMap<String, Tuple<Long, Boolean>> _values = new ConcurrentHashMap<>(); // Map<Key, Tuple<Value,isSynced>>

    protected Boolean _hasLoaded = false;

    protected void _flush() {
        for (final String key : _values.keySet()) {
            final Tuple<Long, Boolean> value = _values.get(key);

            final Boolean isSynced = value.second;
            if ( (isSynced != null) && isSynced ) { continue; }

            try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                if (isSynced == null) {
                    databaseConnection.executeSql(
                        new Query("INSERT IGNORE INTO properties (`key`, value) VALUES (?, ?)")
                            .setParameter(key)
                            .setParameter(value.first)
                    );
                }
                else {
                    databaseConnection.executeSql(
                        new Query("UPDATE properties SET value = ? WHERE `key` = ?")
                            .setParameter(value.first)
                            .setParameter(key)
                    );
                }
                value.second = true;
            }
            catch (final Exception exception) {
                value.second = false;
                Logger.debug(exception);
            }
        }
    }

    protected void _loadValue(final String key) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT value FROM properties WHERE `key` = ?")
            );
            if (rows.isEmpty()) { return; }

            final Row row = rows.get(0);

            final Long value = row.getLong("value");
            _values.put(key, new Tuple<>(value, true));
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }

    public DatabasePropertiesStore(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;

        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Thread thread = Thread.currentThread();
                    while (!thread.isInterrupted()) {
                        synchronized (_mutex) {
                            try { _mutex.wait(); }
                            catch (final Exception exception) { break; }
                        }

                        _flush();
                    }
                }
                finally {
                    _flush();
                }
            }
        });
        _thread.setName("DatabasePropertiesStore - Flush Thread");
        _thread.setDaemon(true);
        _thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
    }

    public synchronized void start() {
        if (_hasLoaded) { return; }

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT `key`, value FROM properties")
            );

            for (final Row row : rows) {
                final String key = row.getString("key");
                final Long value = row.getLong("value");

                _values.put(key, new Tuple<>(value, true));
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        _thread.start();
        _hasLoaded = true;
    }

    public void stop() {
        _thread.interrupt();

        try { _thread.join(); }
        catch (final Exception exception) { }
    }

    @Override
    public synchronized void set(final String key, final Long value) {
        if (_values.containsKey(key)) {
            final Tuple<Long, Boolean> tuple = _values.get(key);
            tuple.first = value;
            tuple.second = false;
        }
        else {
            _values.put(key, new Tuple<>(value, false));
        }

        synchronized (_mutex) {
            _mutex.notifyAll();
        }
    }

    @Override
    public synchronized Long get(final String key) {
        if (! _hasLoaded) {
            Logger.debug("NOTICE: Requested value before start.");
            _loadValue(key);
        }

        final Tuple<Long, Boolean> tuple = _values.get(key);
        if (tuple == null) { return null; }

        return tuple.first;
    }

    @Override
    public synchronized void getAndSet(final String key, final GetAndSetter getAndSetter) {
        if (! _hasLoaded) {
            Logger.debug("NOTICE: Requested value before start.");
            _loadValue(key);
        }

        final Tuple<Long, Boolean> tuple;
        if (_values.containsKey(key)) {
            tuple = _values.get(key);
        }
        else {
            tuple = new Tuple<>(null, true);
            _values.put(key, tuple);
        }

        tuple.first = getAndSetter.run(tuple.first);
        tuple.second = false;

        synchronized (_mutex) {
            _mutex.notifyAll();
        }
    }
}

package com.softwareverde.bitcoin.server.properties;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.query.Query;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.concurrent.ConcurrentHashMap;

public class DatabasePropertiesStore implements PropertiesStore {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final Thread _thread;
    protected final Object _mutex = new Object();
    protected final ConcurrentHashMap<String, Tuple<String, Boolean>> _values = new ConcurrentHashMap<>(); // Map<Key, Tuple<Value,isSynced>>

    protected Boolean _hasLoaded = false;

    protected Boolean _keyExists(final String key, final DatabaseConnection databaseConnection) throws DatabaseException {
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT 1 FROM properties WHERE `key` = ?")
                .setParameter(key)
        );
        return (rows.size() > 0);
    }

    protected void _flush() {
        for (final String key : _values.keySet()) {
            final Tuple<String, Boolean> value = _values.get(key);

            final Boolean isSynced = value.second;
            if (isSynced) { continue; }

            try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final Boolean keyExists = _keyExists(key, databaseConnection);
                if (keyExists) {
                    databaseConnection.executeSql(
                        new Query("UPDATE properties SET value = ? WHERE `key` = ?")
                            .setParameter(value.first)
                            .setParameter(key)
                    );
                }
                else {
                    databaseConnection.executeSql(
                        new Query("INSERT INTO properties (`key`, value) VALUES (?, ?)")
                            .setParameter(key)
                            .setParameter(value.first)
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
                    .setParameter(key)
            );
            if (rows.isEmpty()) { return; }

            final Row row = rows.get(0);

            final String value = row.getString("value");
            _values.put(key, new Tuple<>(value, true));
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }

    protected <T> T _getValue(final String key, final Converter<T> converter) {
        if (! _hasLoaded) {
            Logger.debug("NOTICE: Requested value before start. (" + key + ")", new Exception());
            _loadValue(key);
        }

        final Tuple<String, Boolean> tuple = _values.get(key);
        if (tuple == null) { return null; }

        return converter.toType(tuple.first);
    }

    protected <T> void _setValue(final String key, final T value, final Converter<T> converter) {
        final String stringValue = converter.toString(value);

        final boolean flushIsRequired;
        if (_values.containsKey(key)) {
            final Tuple<String, Boolean> tuple = _values.get(key);
            if (! Util.areEqual(tuple.first, value)) {
                tuple.first = stringValue;
                tuple.second = false;
                flushIsRequired = true;
            }
            else {
                flushIsRequired = false;
            }
        }
        else {
            _values.put(key, new Tuple<>(stringValue, false));
            flushIsRequired = true;
        }

        if (flushIsRequired) {
            synchronized (_mutex) {
                _mutex.notifyAll();
            }
        }
    }

    protected <T> void _getAndSet(final String key, final GetAndSetter<T> getAndSetter, final Converter<T> converter) {
        if (! _hasLoaded) {
            Logger.debug("NOTICE: Requested value before start.");
            _loadValue(key);
        }

        final Tuple<String, Boolean> tuple;
        if (_values.containsKey(key)) {
            tuple = _values.get(key);
        }
        else {
            tuple = new Tuple<>(null, true);
            _values.put(key, tuple);
        }

        final boolean flushIsRequired;
        final T previousValue = converter.toType(tuple.first);
        final T newValue = getAndSetter.run(previousValue);
        tuple.first = converter.toString(newValue);

        if (! Util.areEqual(previousValue, tuple.first)) {
            tuple.second = false;
            flushIsRequired = true;
        }
        else {
            flushIsRequired = false;
        }

        if (flushIsRequired) {
            synchronized (_mutex) {
                _mutex.notifyAll();
            }
        }
    }

    public DatabasePropertiesStore(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;

        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Thread thread = Thread.currentThread();
                    while (! thread.isInterrupted()) {
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
                final String value = row.getString("value");

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
        _setValue(key, value, Converter.LONG);
    }

    @Override
    public synchronized void set(final String key, final String value) {
        _setValue(key, value, Converter.STRING);
    }

    @Override
    public synchronized Long getLong(final String key) {
        return _getValue(key, Converter.LONG);
    }

    @Override
    public String getString(final String key) {
        return _getValue(key, Converter.STRING);
    }

    @Override
    public synchronized void getAndSetLong(final String key, final GetAndSetter<Long> getAndSetter) {
        _getAndSet(key, getAndSetter, Converter.LONG);
    }

    @Override
    public void getAndSetString(final String key, final GetAndSetter<String> getAndSetter) {
        _getAndSet(key, getAndSetter, Converter.STRING);
    }
}

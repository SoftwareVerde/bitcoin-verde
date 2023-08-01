package com.softwareverde.bitcoin.server.properties;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public class DiskPropertiesStore implements PropertiesStore, Jsonable {
    protected final File _file;
    protected final Thread _thread;
    protected final Object _mutex = new Object();
    protected final ConcurrentHashMap<String, String> _values = new ConcurrentHashMap<>();

    protected <T> void _setValue(final String key, final T value, final Converter<T> converter) {
        final String stringValue = converter.toString(value);
        _values.put(key, stringValue);

        synchronized (_mutex) {
            _mutex.notifyAll();
        }
    }

    protected <T> T _getValue(final String key, final Converter<T> converter) {
        final String stringValue = _values.get(key);
        return converter.toType(stringValue);
    }

    protected <T> void _getAndSet(final String key, final GetAndSetter<T> getAndSetter, final Converter<T> converter) {
        final String stringValue = _values.get(key);
        final T oldValue = converter.toType(stringValue);
        final T newValue = getAndSetter.run(oldValue);
        final String newValueString = converter.toString(newValue);
        _values.put(key, newValueString);

        synchronized (_mutex) {
            _mutex.notifyAll();
        }
    }

    public DiskPropertiesStore(final File dataDirectory) {
        _file = new File(dataDirectory, "properties.dat");

        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Sha256Hash oldContentHash;
                    {
                        final ByteArray storedBytes = ByteArray.wrap(Util.coalesce(IoUtil.getFileContents(_file), new byte[0]));
                        oldContentHash = HashUtil.sha256(storedBytes);
                    }

                    final Thread thread = Thread.currentThread();
                    while (! thread.isInterrupted()) {
                        synchronized (_mutex) {
                            try { _mutex.wait(); }
                            catch (final Exception exception) { break; }
                        }

                        final Sha256Hash currentContentHash;
                        final ByteArray contentBytes;
                        {
                            final Json json = DiskPropertiesStore.this.toJson();
                            final String content = json.toString();
                            contentBytes = ByteArray.wrap(StringUtil.stringToBytes(content));
                            currentContentHash = HashUtil.sha256(contentBytes);
                        }
                        if (! Util.areEqual(oldContentHash, currentContentHash)) {
                            IoUtil.putFileContents(_file, contentBytes);
                        }
                    }
                }
                finally {
                    final Json json = DiskPropertiesStore.this.toJson();
                    final String content = json.toString();
                    IoUtil.putFileContents(_file, StringUtil.stringToBytes(content));
                }
            }
        });
        _thread.setName("DiskPropertiesStore - Flush Thread");
        _thread.setDaemon(true);
        _thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });

        final String oldContent = StringUtil.bytesToString(Util.coalesce(IoUtil.getFileContents(_file), new byte[0]));
        final Json json = Json.parse(oldContent);
        for (final String key : json.getKeys()) {
            final String value = json.getString(key);
            _values.put(key, value);
        }
    }

    public void start() {
        _thread.start();
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

    }

    @Override
    public Json toJson() {
        final Json json = new Json(false);
        for (final String key : _values.keySet()) {
            final Object value = _values.get(key);

            json.put(key, value);
        }
        return json;
    }
}

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
    protected final ConcurrentHashMap<String, Long> _values = new ConcurrentHashMap<>();

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
            final Long value = json.getLong(key);
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
        _values.put(key, value);

        synchronized (_mutex) {
            _mutex.notifyAll();
        }
    }

    @Override
    public synchronized Long get(final String key) {
        return _values.get(key);
    }

    @Override
    public synchronized void getAndSet(final String key, final GetAndSetter getAndSetter) {
        final Long value = _values.get(key);
        final Long newValue = getAndSetter.run(value);
        _values.put(key, newValue);

        synchronized (_mutex) {
            _mutex.notifyAll();
        }
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

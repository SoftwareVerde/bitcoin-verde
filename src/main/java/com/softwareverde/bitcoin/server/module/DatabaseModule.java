package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.Environment;

public class DatabaseModule {
    protected final Environment _environment;

    public DatabaseModule(final Environment environment) {
        _environment = environment;
    }

    public void loop() {
        while (true) {
            try { Thread.sleep(5000); } catch (final Exception exception) { break; }
        }
    }
}

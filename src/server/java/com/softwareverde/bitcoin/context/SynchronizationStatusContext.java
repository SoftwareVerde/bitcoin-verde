package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.server.SynchronizationStatus;

public interface SynchronizationStatusContext {
    SynchronizationStatus getSynchronizationStatus();
}

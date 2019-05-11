package com.softwareverde.bitcoin.server;

public interface SynchronizationStatus {
    State getState();
    Boolean isBlockchainSynchronized();
    Boolean isReadyForTransactions();
    Long getCurrentBlockHeight();
}

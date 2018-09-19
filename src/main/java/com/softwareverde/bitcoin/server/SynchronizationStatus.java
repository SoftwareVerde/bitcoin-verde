package com.softwareverde.bitcoin.server;

public interface SynchronizationStatus {
    enum State {
        ONLINE, SYNCHRONIZING, WAITING_FOR_BLOCK
    }

    State getState();
    Boolean isBlockChainSynchronized();
    Boolean isReadyForTransactions();
    Integer getCurrentBlockHeight();
}

package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class SynchronizationStatusHandler implements SynchronizationStatus {
    protected final SystemTime _systemTime = new SystemTime();
    protected final Blockchain _blockchain;

    protected State _state = State.ONLINE;

    public SynchronizationStatusHandler(final Blockchain blockchain) {
        _blockchain = blockchain;
    }

    public void setState(final State state) {
        if (! Util.areEqual(_state, state)) {
            Logger.info("Synchronization State: " + state);
        }
        _state = state;
    }

    @Override
    public State getState() {
        return _state;
    }

    @Override
    public Boolean isBlockchainSynchronized() {
        return (_state == State.ONLINE);
    }

    @Override
    public Boolean isReadyForTransactions() {
        return (_state == State.ONLINE);
    }

    @Override
    public Boolean isShuttingDown() {
        return (_state == State.SHUTTING_DOWN);
    }

    @Override
    public Long getCurrentBlockHeight() {
        return _blockchain.getHeadBlockHeight();
    }
}

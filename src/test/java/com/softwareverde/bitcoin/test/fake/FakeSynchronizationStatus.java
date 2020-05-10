package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.SynchronizationStatus;

public class FakeSynchronizationStatus implements SynchronizationStatus {
    protected State _state = State.ONLINE;
    protected Long _currentBlockHeight = Long.MAX_VALUE;

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
        return _currentBlockHeight;
    }

    public void setState(final State state) {
        _state = state;
    }

    public void setCurrentBlockHeight(final Long currentBlockHeight) {
        _currentBlockHeight = currentBlockHeight;
    }
}

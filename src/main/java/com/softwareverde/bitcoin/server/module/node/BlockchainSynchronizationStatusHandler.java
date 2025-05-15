package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class BlockchainSynchronizationStatusHandler implements SynchronizationStatus {
    protected final SystemTime _systemTime = new SystemTime();
    protected final Blockchain _blockchain;
    protected boolean _isShuttingDown = false;

    protected State _state = State.SYNCHRONIZING;

    protected void _calculateState() {
        if (_isShuttingDown) {
            _state = State.SHUTTING_DOWN;
            return;
        }

        final long offset = 12L * 60L * 60L;
        final Long headerHeight = _blockchain.getHeadBlockHeaderHeight();
        final Long blockHeight = _blockchain.getHeadBlockHeight();

        if (blockHeight < headerHeight) {
            _state = State.SYNCHRONIZING;
            return;
        }

        final BlockHeader blockHeader = _blockchain.getBlockHeader(headerHeight);
        if (blockHeader.getTimestamp() < (_systemTime.getCurrentTimeInSeconds() - offset)) {
            _state = State.SYNCHRONIZING;
            return;
        }

        _state = State.ONLINE;
    }

    public BlockchainSynchronizationStatusHandler(final Blockchain blockchain) {
        _blockchain = blockchain;
    }

    public void setState(final State state) {
        if (! Util.areEqual(_state, state)) {
            Logger.info("Synchronization State: " + state);
        }
        _state = state;
    }

    public void setIsShuttingDown(final Boolean isShuttingDown) {
        _isShuttingDown = isShuttingDown;
    }

    public void recalculateState() {
        _calculateState();
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

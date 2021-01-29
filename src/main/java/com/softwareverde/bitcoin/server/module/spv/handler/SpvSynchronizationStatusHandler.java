package com.softwareverde.bitcoin.server.module.spv.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class SpvSynchronizationStatusHandler implements SynchronizationStatus {
    protected final SystemTime _systemTime = new SystemTime();
    protected final DatabaseManagerFactory _databaseManagerFactory;

    protected State _state = State.ONLINE;

    public SpvSynchronizationStatusHandler(final DatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
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
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final Long blockHeaderTimestampInSeconds;
            {
                final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                if (headBlockHeaderId == null) { return false; }

                final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(headBlockHeaderId);
                blockHeaderTimestampInSeconds = blockHeader.getTimestamp();
            }

            final long secondsBehind = (_systemTime.getCurrentTimeInSeconds() - blockHeaderTimestampInSeconds);
            final int secondsInAnHour = (60 * 60);
            return (secondsBehind < (24 * secondsInAnHour));
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return false;
        }
    }

    @Override
    public Boolean isShuttingDown() {
        return (_state == State.SHUTTING_DOWN);
    }

    @Override
    public Long getCurrentBlockHeight() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            if (headBlockHeaderId == null) { return 0L; }

            return blockHeaderDatabaseManager.getBlockHeight(headBlockHeaderId);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return 0L;
        }
    }
}

package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.type.time.SystemTime;

public class SynchronizationStatusHandler implements SynchronizationStatus {
    protected final SystemTime _systemTime = new SystemTime();
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected State _state = State.ONLINE;

    public SynchronizationStatusHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    public void setState(final State state) {
        Logger.log("Synchronization State: " + state);
        _state = state;
    }

    @Override
    public State getState() {
        return _state;
    }

    @Override
    public Boolean isBlockChainSynchronized() {
        return (_state == State.WAITING_FOR_BLOCK);
    }

    @Override
    public Boolean isReadyForTransactions() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {

            final Long blockTimestampInSeconds;
            {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
                final Sha256Hash lastKnownHash = blockDatabaseManager.getHeadBlockHash();
                if (lastKnownHash == null) {
                    blockTimestampInSeconds = MedianBlockTime.GENESIS_BLOCK_TIMESTAMP;
                }
                else {
                    final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(lastKnownHash);
                    final BlockHeader blockHeader = blockDatabaseManager.getBlockHeader(blockId);
                    blockTimestampInSeconds = blockHeader.getTimestamp();
                }
            }

            final Long secondsBehind = (_systemTime.getCurrentTimeInSeconds() - blockTimestampInSeconds);

            final Integer secondsInAnHour = (60 * 60);
            return (secondsBehind < (24 * secondsInAnHour));
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }
    }

    @Override
    public Integer getCurrentBlockHeight() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockId blockId = blockDatabaseManager.getHeadBlockId();
            if (blockId == null) { return 0; }

            final Long blockHeight = blockDatabaseManager.getBlockHeightForBlockId(blockId);
            return blockHeight.intValue();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return 0;
        }
    }
}

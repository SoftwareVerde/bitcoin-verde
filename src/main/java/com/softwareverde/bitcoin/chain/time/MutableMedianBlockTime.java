package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MutableMedianBlockTime implements MedianBlockTime, MedianBlockTimeWithBlocks {
    protected static MutableMedianBlockTime _newInitializedMedianBlockTime(final MysqlDatabaseConnection databaseConnection, final Sha256Hash headBlockHash) throws DatabaseException{
        // Initializes medianBlockTime with the N most recent blocks...

        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        Sha256Hash blockHash = headBlockHash;
        for (int i = 0; i < MedianBlockTime.BLOCK_COUNT; ++i) {
            final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(blockHash);
            if (blockId == null) { break; }

            final BlockHeader blockHeader = blockDatabaseManager.getBlockHeader(blockId);
            medianBlockTime.addBlock(blockHeader);
            blockHash = blockHeader.getPreviousBlockHash();
        }

        return medianBlockTime;
    }

    public static MutableMedianBlockTime newInitializedMedianBlockTime(final MysqlDatabaseConnection databaseConnection) {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        try {
            Sha256Hash blockHash = Util.coalesce(blockDatabaseManager.getHeadBlockHash(), Block.GENESIS_BLOCK_HASH);
            return _newInitializedMedianBlockTime(databaseConnection, blockHash);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    public static MutableMedianBlockTime newInitializedMedianBlockHeaderTime(final MysqlDatabaseConnection databaseConnection) {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        try {
            Sha256Hash blockHash = Util.coalesce(blockDatabaseManager.getHeadBlockHeaderHash(), Block.GENESIS_BLOCK_HASH);
            return _newInitializedMedianBlockTime(databaseConnection, blockHash);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    protected final Integer _requiredBlockCount;
    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;
    protected final RotatingQueue<BlockHeader> _previousBlocks;

    protected Long _getMedianBlockTimeInMilliseconds() {
        final Integer blockCount = _previousBlocks.size();

        if (blockCount < _requiredBlockCount) {
            // Logger.log("NOTICE: Attempted to retrieve MedianBlockTime without setting at least " + _requiredBlockCount + " blocks.");
            return MedianBlockTime.GENESIS_BLOCK_TIMESTAMP;
        }

        final List<Long> blockTimestamps = new ArrayList<Long>(blockCount);
        for (final BlockHeader block : _previousBlocks) {
            blockTimestamps.add(block.getTimestamp());
        }
        Collections.sort(blockTimestamps);

        final int index = (blockCount / 2); // Typically the 6th block (index 5) of 11...
        return (blockTimestamps.get(index) * 1000L);
    }

    protected MutableMedianBlockTime(final Integer requiredBlockCount) {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

        _requiredBlockCount = requiredBlockCount;
        _previousBlocks = new RotatingQueue<BlockHeader>(_requiredBlockCount);
    }

    public MutableMedianBlockTime() {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

        _requiredBlockCount = BLOCK_COUNT;
        _previousBlocks = new RotatingQueue<BlockHeader>(_requiredBlockCount);
    }

    public void addBlock(final BlockHeader blockHeader) {
        try {
            _writeLock.lock();
            _previousBlocks.add(blockHeader);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public Boolean hasRequiredBlockCount() {
        final Boolean hasRequiredBlockCount;
        try {
            _readLock.lock();
            hasRequiredBlockCount = (_previousBlocks.size() >= _requiredBlockCount);
        }
        finally {
            _readLock.unlock();
        }

        return hasRequiredBlockCount;
    }

    @Override
    public MedianBlockTime subset(final Integer blockCount) {
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime(blockCount);

        final java.util.List<BlockHeader> blockHeaders = new java.util.ArrayList<BlockHeader>(_previousBlocks.size());
        blockHeaders.addAll(_previousBlocks);

        for (int i = 0; i < blockCount; ++i) {
            if (i >= blockHeaders.size()) { break; }
            final BlockHeader blockHeader = blockHeaders.get(blockHeaders.size() - i - 1);
            medianBlockTime.addBlock(blockHeader);
        }

        return medianBlockTime;
    }

    @Override
    public BlockHeader getBlockHeader(final Integer indexFromTip) {
        final java.util.List<BlockHeader> blockHeaders = new java.util.ArrayList<BlockHeader>(_previousBlocks.size());
        blockHeaders.addAll(_previousBlocks);
        return blockHeaders.get(blockHeaders.size() - indexFromTip - 1);
    }

    @Override
    public Long getCurrentTimeInSeconds() {
        final Long medianBlockTime;
        try {
            _readLock.lock();
            medianBlockTime = _getMedianBlockTimeInMilliseconds();
        }
        finally {
            _readLock.unlock();
        }

        return (medianBlockTime / 1000L);
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        final Long medianBlockTime;
        try {
            _readLock.lock();
            medianBlockTime = _getMedianBlockTimeInMilliseconds();
        }
        finally {
            _readLock.unlock();
        }

        return medianBlockTime;
    }

    @Override
    public ImmutableMedianBlockTime asConst() {
        return new ImmutableMedianBlockTime(this);
    }
}

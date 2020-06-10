package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

public class LazyMedianBlockTimeSet implements MedianBlockTimeContext {
    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final CircleBuffer<Tuple<Sha256Hash, MedianBlockTime>> _cachedMedianBlockTimes = new CircleBuffer<Tuple<Sha256Hash, MedianBlockTime>>(64);

    protected MedianBlockTime _getCachedMedianBlockTime(final Sha256Hash blockHash) {
        synchronized (_cachedMedianBlockTimes) {
            for (final Tuple<Sha256Hash, MedianBlockTime> medianBlockTimeTuple : _cachedMedianBlockTimes) {
                if (Util.areEqual(medianBlockTimeTuple.first, blockHash)) {
                    return medianBlockTimeTuple.second;
                }
            }
        }
        return null;
    }

    protected void _cacheMedianBlockTime(final Sha256Hash blockHash, final MedianBlockTime medianBlockTime) {
        synchronized (_cachedMedianBlockTimes) {
            _cachedMedianBlockTimes.push(new Tuple<Sha256Hash, MedianBlockTime>(blockHash, medianBlockTime));
        }
    }

    protected MedianBlockTime _getMedianBlockTime(final Sha256Hash blockHash, final DatabaseManager databaseManager) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTimeBefore(blockId);

        _cacheMedianBlockTime(blockHash, medianBlockTime);

        return medianBlockTime;
    }

    public LazyMedianBlockTimeSet(final BlockchainSegmentId blockchainSegmentId, final DatabaseManagerFactory databaseManagerFactory) {
        _blockchainSegmentId = blockchainSegmentId;
        _databaseManagerFactory = databaseManagerFactory;
    }

    public MedianBlockTime getMedianBlockTime(final Sha256Hash blockHash) {
        synchronized (_cachedMedianBlockTimes) {
            for (final Tuple<Sha256Hash, MedianBlockTime> medianBlockTimeTuple : _cachedMedianBlockTimes) {
                if (Util.areEqual(medianBlockTimeTuple.first, blockHash)) {
                    return medianBlockTimeTuple.second;
                }
            }
        }

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTimeBefore(blockId);

            synchronized (_cachedMedianBlockTimes) {
                _cachedMedianBlockTimes.push(new Tuple<Sha256Hash, MedianBlockTime>(blockHash, medianBlockTime));
            }

            return medianBlockTime;
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(_blockchainSegmentId, blockHeight);
            if (blockId == null) { return null; }

            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);

            final MedianBlockTime cachedMedianBlockTime = _getCachedMedianBlockTime(blockHash);
            if (cachedMedianBlockTime != null) { return cachedMedianBlockTime; }

            return _getMedianBlockTime(blockHash, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }
}

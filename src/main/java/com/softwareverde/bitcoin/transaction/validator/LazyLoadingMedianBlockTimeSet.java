package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

public class LazyLoadingMedianBlockTimeSet implements MedianBlockTimeSet {
    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final CircleBuffer<Tuple<Sha256Hash, MedianBlockTime>> _cachedMedianBlockTimes = new CircleBuffer<Tuple<Sha256Hash, MedianBlockTime>>(64);

    public LazyLoadingMedianBlockTimeSet(final DatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    public void setBlockchainSegmentId(final BlockchainSegmentId blockchainSegmentId) {
        // Clear the cache...
        synchronized (_cachedMedianBlockTimes) {
            final int itemCount = _cachedMedianBlockTimes.getCount();
            for (int i = 0; i < itemCount; ++i) {
                _cachedMedianBlockTimes.pop();
            }
        }
    }

    @Override
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
            final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTime(blockId);

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
}

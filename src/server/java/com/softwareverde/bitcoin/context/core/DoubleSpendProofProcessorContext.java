package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof.DoubleSpendProofProcessor;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class DoubleSpendProofProcessorContext implements DoubleSpendProofProcessor.Context {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final UpgradeSchedule _upgradeSchedule;

    public DoubleSpendProofProcessorContext(final FullNodeDatabaseManagerFactory databaseManagerFactory, final UpgradeSchedule upgradeSchedule) {
        _databaseManagerFactory = databaseManagerFactory;
        _upgradeSchedule = upgradeSchedule;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);
            if (blockId == null) { return null; }

            return blockHeaderDatabaseManager.getMedianBlockTime(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }

    @Override
    public FullNodeDatabaseManagerFactory getDatabaseManagerFactory() {
        return _databaseManagerFactory;
    }
}

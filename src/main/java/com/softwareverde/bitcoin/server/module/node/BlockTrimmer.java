package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.input.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

public class BlockTrimmer {
    protected final CoreDatabaseManagerFactory _databaseManagerFactory;

    protected void _trimBlock(final BlockId blockId, final CoreDatabaseManager databaseManager) throws DatabaseException {
        final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
        final TransactionInputDatabaseManager transactionInputDatabaseManager = databaseManager.getTransactionInputDatabaseManager();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = databaseManager.getTransactionOutputDatabaseManager();

        final List<TransactionId> blockTransactionIds = blockDatabaseManager.getTransactionIds(blockId);
        for (final TransactionId transactionId : blockTransactionIds) {
            final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIds(transactionId);
            if (transactionInputIds == null) { continue; }

            for (final TransactionInputId transactionInputId : transactionInputIds) {
                final TransactionOutputId transactionOutputId = transactionInputDatabaseManager.getPreviousTransactionOutputId(transactionInputId);
                if (transactionOutputId == null) { continue; }

                // Logger.log("Trimming Transaction Output Id: " + transactionOutputId);
                transactionOutputDatabaseManager.deleteTransactionOutput(transactionOutputId);
            }
        }
    }

    public BlockTrimmer(final CoreDatabaseManagerFactory databaseConnectionFactory) {
        _databaseManagerFactory = databaseConnectionFactory;
    }

    public void trimBlock(final Long blockHeight) throws DatabaseException {
        try (final CoreDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);

            _trimBlock(blockId, databaseManager);
        }
    }

    public void trimBlock(final Sha256Hash blockHash) throws DatabaseException {
        try (final CoreDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

            _trimBlock(blockId, databaseManager);
        }
    }

    public void trimBlock(final BlockId blockId) throws DatabaseException {
        try (final CoreDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _trimBlock(blockId, databaseManager);
        }
    }

    public void trimBlock(final Sha256Hash childBlockHash, final Integer parentCount) throws DatabaseException {
        try (final CoreDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId childBlockId = blockHeaderDatabaseManager.getBlockHeaderId(childBlockHash);
            final BlockId ancestorBlockId = blockHeaderDatabaseManager.getAncestorBlockId(childBlockId, parentCount);

            _trimBlock(ancestorBlockId, databaseManager);
        }
    }
}

package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.*;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;

public class BlockTrimmer {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected void _trimBlock(final BlockId blockId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(databaseConnection, _databaseManagerCache);
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection, _databaseManagerCache);

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

    public BlockTrimmer(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    public void trimBlock(final Long blockHeight) throws DatabaseException {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);

            _trimBlock(blockId, databaseConnection);
        }
    }

    public void trimBlock(final Sha256Hash blockHash) throws DatabaseException {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

            _trimBlock(blockId, databaseConnection);
        }
    }

    public void trimBlock(final BlockId blockId) throws DatabaseException {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            _trimBlock(blockId, databaseConnection);
        }
    }

    public void trimBlock(final Sha256Hash childBlockHash, final Integer parentCount) throws DatabaseException {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockId childBlockId = blockHeaderDatabaseManager.getBlockHeaderId(childBlockHash);
            final BlockId ancestorBlockId = blockHeaderDatabaseManager.getAncestorBlockId(childBlockId, parentCount);

            _trimBlock(ancestorBlockId, databaseConnection);
        }
    }
}

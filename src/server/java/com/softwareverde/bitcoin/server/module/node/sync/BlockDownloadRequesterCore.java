package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

/**
 * BlockDownloadRequesterCore stores a PendingBlock record for the requested Block and notifies the BlockDownloader.
 *  If the block is high-priority and no peers currently have the block, a blockfinder is emitted.
 */
public class BlockDownloadRequesterCore implements BlockDownloadRequester {
    protected final SystemTime _systemTime = new SystemTime();

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected final BlockDownloader _blockDownloader;
    protected final BitcoinNodeManager _bitcoinNodeManager;

    protected final Object _lastUnavailableRequestedBlockTimestampMutex = new Object();
    protected Long _lastUnavailableRequestedBlockTimestamp = 0L;

    protected Sha256Hash _getParentBlockHash(final Sha256Hash childBlockHash, final DatabaseManager databaseManager) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final BlockId childBlockId = blockHeaderDatabaseManager.getBlockHeaderId(childBlockHash);
        if (childBlockId == null) { return null; }

        final BlockId parentBlockId = blockHeaderDatabaseManager.getAncestorBlockId(childBlockId, 1);
        if (parentBlockId == null) { return null; }

        return blockHeaderDatabaseManager.getBlockHash(parentBlockId);
    }

    protected void _requestBlock(final FullNodeDatabaseManager databaseManager, final Sha256Hash blockHash, final Sha256Hash parentBlockHash, final Long priority) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

        if (priority >= 256) {
            final Boolean pendingBlockConnectedToMainChain = pendingBlockDatabaseManager.isPendingBlockConnectedToMainChain(blockHash);
            if (! Util.coalesce(pendingBlockConnectedToMainChain, true)) { return; }
        }

        TransactionUtil.startTransaction(databaseConnection);
        final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlockHash(blockHash, parentBlockHash);
        pendingBlockDatabaseManager.setPriority(pendingBlockId, priority);
        TransactionUtil.commitTransaction(databaseConnection);

        _blockDownloader.wakeUp();
    }

    public BlockDownloadRequesterCore(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BlockDownloader blockDownloader, final BitcoinNodeManager bitcoinNodeManager) {
        _databaseManagerFactory = databaseManagerFactory;
        _blockDownloader = blockDownloader;
        _bitcoinNodeManager = bitcoinNodeManager;
    }

    @Override
    public void requestBlock(final BlockHeader blockHeader) {
        final Sha256Hash blockHash = blockHeader.getHash();
        final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
        final Long timestamp = blockHeader.getTimestamp();

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _requestBlock(databaseManager, blockHash, previousBlockHash, timestamp);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }

    @Override
    public void requestBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _requestBlock(databaseManager, blockHash, previousBlockHash, 0L);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }

    @Override
    public void requestBlocks(final List<BlockHeader> blockHeaders) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            for (final BlockHeader blockHeader : blockHeaders) {
                final Sha256Hash blockHash = blockHeader.getHash();
                final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
                final Long timestamp = blockHeader.getTimestamp();

                _requestBlock(databaseManager, blockHash, previousBlockHash, timestamp);
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }
}

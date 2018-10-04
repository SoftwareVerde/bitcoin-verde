package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.SleepyService;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.manager.ThreadPool;
import com.softwareverde.util.Util;

public class BlockChainBuilder extends SleepyService {
    public interface NewBlockProcessedCallback {
        void newBlockHeight(Long blockHeight);
    }

    protected final ThreadPool _threadPool = new ThreadPool(0, 1, 60000L);
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected final BlockProcessor _blockProcessor;
    protected final BlockDownloader.StatusMonitor _downloadStatusMonitor;
    protected final BlockDownloadRequester _blockDownloadRequester;
    protected Boolean _hasGenesisBlock;
    protected NewBlockProcessedCallback _newBlockProcessedCallback = null;

    protected Boolean _processPendingBlock(final PendingBlock pendingBlock) {
        final ByteArray blockData = pendingBlock.getData();
        if (blockData == null) { return false; } // NOTE: The pending block is not available due to a race condition; do not delete the pending block record in this case.

        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(blockData);

        if (block != null) {
            final Long processedBlockHeight = _blockProcessor.processBlock(block);
            final Boolean blockWasValid = (processedBlockHeight != null);

            if (blockWasValid) {
                final NewBlockProcessedCallback newBlockProcessedCallback = _newBlockProcessedCallback;
                if (newBlockProcessedCallback != null) {
                    _threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            newBlockProcessedCallback.newBlockHeight(processedBlockHeight);
                        }
                    });
                }
            }

            return blockWasValid;
        }
        else {
            Logger.log("NOTICE: Pending Block Corrupted: " + pendingBlock.getBlockHash() + " " + blockData);
            return false;
        }
    }

    protected Boolean _processGenesisBlock(final PendingBlockId pendingBlockId, final MysqlDatabaseConnection databaseConnection, final PendingBlockDatabaseManager pendingBlockDatabaseManager) throws DatabaseException {
        final PendingBlock pendingBlock = pendingBlockDatabaseManager.getPendingBlock(pendingBlockId);
        final ByteArray blockData = pendingBlock.getData();
        if (blockData == null) { return false; }

        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(blockData);
        if (block == null) { return false; }

        final BlockId blockId;
        final BlockHasher blockHasher = new BlockHasher();
        final Sha256Hash blockHash = blockHasher.calculateBlockHash(block);
        if (Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, blockHash)) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);
            blockId = blockDatabaseManager.storeBlock(BlockDatabaseManager.MUTEX, block);
        }
        else {
            blockId = null;
        }

        TransactionUtil.startTransaction(databaseConnection);
        pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);
        TransactionUtil.commitTransaction(databaseConnection);

        return (blockId != null);
    }

    @Override
    protected void _onStart() { }

    @Override
    public Boolean _run() {
        final Thread thread = Thread.currentThread();

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

            { // Special case for storing the Genesis block...
                if (! _hasGenesisBlock) {
                    final PendingBlockId genesisPendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(BlockHeader.GENESIS_BLOCK_HASH);
                    final Boolean hasBlockDataAvailable = pendingBlockDatabaseManager.hasBlockData(genesisPendingBlockId);

                    final Boolean genesisBlockWasLoaded;
                    if (hasBlockDataAvailable) {
                        genesisBlockWasLoaded = _processGenesisBlock(genesisPendingBlockId, databaseConnection, pendingBlockDatabaseManager);
                    }
                    else {
                        genesisBlockWasLoaded = false;
                    }

                    if (genesisBlockWasLoaded) {
                        _hasGenesisBlock = true;
                    }
                    else {
                        _blockDownloadRequester.requestBlock(BlockHeader.GENESIS_BLOCK_HASH);
                        return false;
                    }
                }
            }

            while (! thread.isInterrupted()) {
                // Select the first pending block to process; if none are found, request one to be downloaded...
                final PendingBlockId candidatePendingBlockId = pendingBlockDatabaseManager.selectCandidatePendingBlockId();
                if (candidatePendingBlockId == null) {
                    // Request the next head block be downloaded... (depends on BlockHeaders)
                    final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection, _databaseCache);
                    final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);
                    final BlockChainSegmentId headBlockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();
                    final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                    final BlockId nextBlockId = blockDatabaseManager.getChildBlockId(headBlockChainSegmentId, headBlockId);
                    if (nextBlockId != null) {
                        final Sha256Hash nextBlockHash = blockDatabaseManager.getBlockHashFromId(nextBlockId);
                        _blockDownloadRequester.requestBlock(nextBlockHash);
                    }
                    break;
                }

                final PendingBlock candidatePendingBlock = pendingBlockDatabaseManager.getPendingBlock(candidatePendingBlockId);
                final Boolean processCandidateBlockWasSuccessful = _processPendingBlock(candidatePendingBlock);
                if (! processCandidateBlockWasSuccessful) {
                    pendingBlockDatabaseManager.incrementFailedDownloadCount(candidatePendingBlockId);
                    pendingBlockDatabaseManager.deletePendingBlockData(candidatePendingBlockId); // NOTE: Also prevents the failed block from being returned within PendingBlockDatabaseManager::selectCandidatePendingBlockId during the next iteration...
                    continue;
                }

                pendingBlockDatabaseManager.deletePendingBlock(candidatePendingBlockId);

                PendingBlock previousPendingBlock = candidatePendingBlock;
                while (! thread.isInterrupted()) {
                    final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(previousPendingBlock.getBlockHash());
                    if (pendingBlockIds.isEmpty()) { break; }

                    for (final PendingBlockId pendingBlockId : pendingBlockIds) {
                        final PendingBlock pendingBlock = pendingBlockDatabaseManager.getPendingBlock(pendingBlockId); // NOTE: In the case of a fork, this effectively arbitrarily selects one and relies on the next iteration to process the neglected branch.

                        final Boolean processBlockWasSuccessful = _processPendingBlock(pendingBlock);
                        if (! processBlockWasSuccessful) {
                            pendingBlockDatabaseManager.incrementFailedDownloadCount(pendingBlockId);
                            pendingBlockDatabaseManager.deletePendingBlockData(pendingBlockId);
                            break;
                        }

                        pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);

                        previousPendingBlock = pendingBlock;
                    }
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() {
        final Status downloadStatus = _downloadStatusMonitor.getStatus();
        if (downloadStatus != Status.ACTIVE) {
            try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseConnection, _databaseCache);
                final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();
                _bitcoinNodeManager.broadcastBlockFinder(blockFinderHashes);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
            }
        }
    }

    public BlockChainBuilder(final BitcoinNodeManager bitcoinNodeManager, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache, final BlockProcessor blockProcessor, final BlockDownloader.StatusMonitor downloadStatusMonitor, final BlockDownloadRequester blockDownloadRequester) {
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;
        _blockProcessor = blockProcessor;
        _downloadStatusMonitor = downloadStatusMonitor;
        _blockDownloadRequester = blockDownloadRequester;

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);
            _hasGenesisBlock = blockDatabaseManager.blockExists(BlockHeader.GENESIS_BLOCK_HASH);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            _hasGenesisBlock = false;
        }
    }

    public void setNewBlockProcessedCallback(final NewBlockProcessedCallback newBlockProcessedCallback) {
        _newBlockProcessedCallback = newBlockProcessedCallback;
    }
}

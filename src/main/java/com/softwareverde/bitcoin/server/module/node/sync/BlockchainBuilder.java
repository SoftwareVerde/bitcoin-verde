package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.manager.FilterType;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.manager.ThreadPool;
import com.softwareverde.util.Util;

import java.util.HashMap;

public class BlockchainBuilder extends SleepyService {
    public interface NewBlockProcessedCallback {
        void onNewBlock(Long blockHeight);
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
        if (blockData == null) { return false; }

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
                            newBlockProcessedCallback.onNewBlock(processedBlockHeight);
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
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockId = blockDatabaseManager.storeBlock(block);
            }
        }
        else {
            blockId = null;
        }

        TransactionUtil.startTransaction(databaseConnection);
        pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);
        TransactionUtil.commitTransaction(databaseConnection);

        return (blockId != null);
    }

    protected void _broadcastBlock(final PendingBlock pendingBlock, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);

        final HashMap<NodeId, BitcoinNode> bitcoinNodeMap = new HashMap<NodeId, BitcoinNode>();
        final List<NodeId> connectedNodeIds;
        {
            final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
            final ImmutableListBuilder<NodeId> nodeIdsBuilder = new ImmutableListBuilder<NodeId>(connectedNodes.getSize());
            for (final BitcoinNode bitcoinNode : connectedNodes) {
                final NodeId nodeId = bitcoinNode.getId();
                nodeIdsBuilder.add(nodeId);
                bitcoinNodeMap.put(nodeId, bitcoinNode);
            }
            connectedNodeIds = nodeIdsBuilder.build();
        }

        final Sha256Hash blockHash = pendingBlock.getBlockHash();

        final List<NodeId> nodeIdsWithoutBlocks = nodeDatabaseManager.filterNodesViaBlockInventory(connectedNodeIds, blockHash, FilterType.KEEP_NODES_WITHOUT_INVENTORY);
        for (final NodeId nodeId : nodeIdsWithoutBlocks) {
            final BitcoinNode bitcoinNode = bitcoinNodeMap.get(nodeId);
            if (bitcoinNode == null) { continue; }

            _bitcoinNodeManager.transmitBlockHash(bitcoinNode, blockHash);
        }
    }

    @Override
    protected void _onStart() { }

    @Override
    public Boolean _run() {
        final Thread thread = Thread.currentThread();

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            pendingBlockDatabaseManager.purgeFailedPendingBlocks(BlockDownloader.MAX_DOWNLOAD_FAILURE_COUNT);

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
                    final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseCache);
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseCache);
                    final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);
                    final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                    final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                    final BlockId nextBlockId = blockHeaderDatabaseManager.getChildBlockId(headBlockchainSegmentId, headBlockId);
                    if (nextBlockId != null) {
                        final Sha256Hash nextBlockHash = blockHeaderDatabaseManager.getBlockHash(nextBlockId);
                        _blockDownloadRequester.requestBlock(nextBlockHash);
                    }
                    break;
                }

                // Process the first available candidate block...
                final PendingBlock candidatePendingBlock = pendingBlockDatabaseManager.getPendingBlock(candidatePendingBlockId);
                final Boolean processCandidateBlockWasSuccessful = _processPendingBlock(candidatePendingBlock);
                if (! processCandidateBlockWasSuccessful) {
                    pendingBlockDatabaseManager.deletePendingBlock(candidatePendingBlockId);
                    continue;
                }

                _broadcastBlock(candidatePendingBlock, databaseConnection);

                TransactionUtil.startTransaction(databaseConnection);
                pendingBlockDatabaseManager.deletePendingBlock(candidatePendingBlockId);
                TransactionUtil.commitTransaction(databaseConnection);

                // Process the any viable descendant blocks of the candidate block...
                PendingBlock previousPendingBlock = candidatePendingBlock;
                while (! thread.isInterrupted()) {
                    final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(previousPendingBlock.getBlockHash());
                    if (pendingBlockIds.isEmpty()) { break; }

                    for (final PendingBlockId pendingBlockId : pendingBlockIds) {
                        final PendingBlock pendingBlock = pendingBlockDatabaseManager.getPendingBlock(pendingBlockId); // NOTE: In the case of a fork, this effectively arbitrarily selects one and relies on the next iteration to process the neglected branch.

                        final Boolean processBlockWasSuccessful = _processPendingBlock(pendingBlock);
                        if (! processBlockWasSuccessful) {
                            pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);
                            break;
                        }

                        _broadcastBlock(pendingBlock, databaseConnection);

                        TransactionUtil.startTransaction(databaseConnection);
                        pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);
                        TransactionUtil.commitTransaction(databaseConnection);

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

    public BlockchainBuilder(final BitcoinNodeManager bitcoinNodeManager, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache, final BlockProcessor blockProcessor, final BlockDownloader.StatusMonitor downloadStatusMonitor, final BlockDownloadRequester blockDownloadRequester) {
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;
        _blockProcessor = blockProcessor;
        _downloadStatusMonitor = downloadStatusMonitor;
        _blockDownloadRequester = blockDownloadRequester;

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);
            _hasGenesisBlock = blockDatabaseManager.blockHeaderHasTransactions(BlockHeader.GENESIS_BLOCK_HASH);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            _hasGenesisBlock = false;
        }
    }

    public void setNewBlockProcessedCallback(final NewBlockProcessedCallback newBlockProcessedCallback) {
        _newBlockProcessedCallback = newBlockProcessedCallback;
    }

    @Override
    public void stop() {
        super.stop();

        _threadPool.abortAll();
        _threadPool.waitUntilIdle();
    }
}

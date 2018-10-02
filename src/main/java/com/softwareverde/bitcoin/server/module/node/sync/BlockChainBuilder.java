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
import com.softwareverde.util.Util;

public class BlockChainBuilder {
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected final Runnable _coreRunnable;
    protected final BlockProcessor _blockProcessor;
    protected final Object _mutex = new Object();
    protected final BlockDownloader.StatusMonitor _downloadStatusMonitor;
    protected final BlockDownloadRequester _blockDownloadRequester;
    protected Boolean _hasGenesisBlock = false;

    protected volatile boolean _wasNotifiedOfNewBlock = false;
    protected Thread _thread = null;

    protected void _startThread() {
        _wasNotifiedOfNewBlock = true;
        final Thread thread = new Thread(_coreRunnable);
        _thread = thread;
        thread.start();
    }

    protected Sha256Hash _processPendingBlockId(final PendingBlockId pendingBlockId, final MysqlDatabaseConnection databaseConnection, final PendingBlockDatabaseManager pendingBlockDatabaseManager) throws DatabaseException {
        final PendingBlock pendingBlock = pendingBlockDatabaseManager.getPendingBlock(pendingBlockId);
        final ByteArray blockData = pendingBlock.getData();
        if (blockData == null) { return null; } // NOTE: The pending block is not available due to a race condition; do not delete the pending block record in this case.

        Logger.log("+++ BlockChainBuilder - Block Byte Count: " + blockData.getByteCount());
        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(blockData);

        final Sha256Hash blockHash;
        if (block != null) {
            final Boolean blockWasValid = _blockProcessor.processBlock(block);
            blockHash = (blockWasValid ? block.getHash() : null);
        }
        else {
            blockHash = null;

            final Sha256Hash pendingBlockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
            Logger.log("NOTICE: Pending Block Corrupted: " + pendingBlockHash + " " + blockData);
        }

        TransactionUtil.startTransaction(databaseConnection);
        pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);
        TransactionUtil.commitTransaction(databaseConnection);

        return blockHash;
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
            synchronized (BlockDatabaseManager.MUTEX) {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);
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

        _coreRunnable = new Runnable() {
            @Override
            public void run() {
                while (_wasNotifiedOfNewBlock) {
                    Logger.log("+++ BlockChainBuilder - A");
                    _wasNotifiedOfNewBlock = false;

                    try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                        final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
                        Logger.log("+++ BlockChainBuilder - B");
                        { // Special case for storing the Genesis block...
                            if (! _hasGenesisBlock) {
                                Logger.log("+++ BlockChainBuilder - C");
                                final PendingBlockId genesisPendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(BlockHeader.GENESIS_BLOCK_HASH);
                                final Boolean hasBlockDataAvailable = pendingBlockDatabaseManager.hasBlockData(genesisPendingBlockId);

                                final Boolean genesisBlockWasLoaded;
                                if (hasBlockDataAvailable) {
                                    Logger.log("+++ BlockChainBuilder - D");
                                    genesisBlockWasLoaded = _processGenesisBlock(genesisPendingBlockId, databaseConnection, pendingBlockDatabaseManager);
                                }
                                else {
                                    genesisBlockWasLoaded = false;
                                }

                                if (genesisBlockWasLoaded) {
                                    _hasGenesisBlock = true;
                                }
                                else {
                                    Logger.log("+++ BlockChainBuilder - E");
                                    _blockDownloadRequester.requestBlock(BlockHeader.GENESIS_BLOCK_HASH);
                                    break;
                                }
                            }
                        }

                        Logger.log("+++ BlockChainBuilder - F");
                        while (true) {
                            Logger.log("+++ BlockChainBuilder - G");
                            final PendingBlockId candidatePendingBlockId = pendingBlockDatabaseManager.selectCandidatePendingBlockId();
                            if (candidatePendingBlockId == null) {
                                Logger.log("+++ BlockChainBuilder - H");
                                { // Request the next head block be downloaded... (depends on BlockHeaders)
                                    final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection, _databaseCache);
                                    final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);

                                    final BlockChainSegmentId headBlockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();
                                    final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                                    final BlockId nextBlockId = blockDatabaseManager.getChildBlockId(headBlockChainSegmentId, headBlockId);
                                    if (nextBlockId != null) {
                                        final Sha256Hash nextBlockHash = blockDatabaseManager.getBlockHashFromId(nextBlockId);
                                        _blockDownloadRequester.requestBlock(nextBlockHash);
                                    }
                                }
                                Logger.log("+++ BlockChainBuilder - I");
                                break;
                            }

                            Sha256Hash processedBlockHash = _processPendingBlockId(candidatePendingBlockId, databaseConnection, pendingBlockDatabaseManager);

                            Logger.log("+++ BlockChainBuilder - J");
                            while (processedBlockHash != null) {
                                Logger.log("+++ BlockChainBuilder - K");
                                final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(processedBlockHash);
                                if (pendingBlockIds.isEmpty()) { break; }
                                Logger.log("+++ BlockChainBuilder - L");
                                for (final PendingBlockId pendingBlockId : pendingBlockIds) {
                                    processedBlockHash = _processPendingBlockId(pendingBlockId, databaseConnection, pendingBlockDatabaseManager);
                                    Logger.log("+++ BlockChainBuilder - M");
                                }
                                Logger.log("+++ BlockChainBuilder - N");
                            }

                            Logger.log("+++ BlockChainBuilder - O");
                        }
                    }
                    catch (final DatabaseException exception) {
                        Logger.log(exception);
                        try { Thread.sleep(10000L); } catch (final InterruptedException interruptedException) { break; }
                    }
                }

                Logger.log("+++ BlockChainBuilder - P");
                final BlockDownloader.Status downloadStatus = _downloadStatusMonitor.getStatus();
                if (downloadStatus != BlockDownloader.Status.DOWNLOADING) {
                    Logger.log("+++ BlockChainBuilder - Q");
                    try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                        final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseConnection, _databaseCache);
                        final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();
                        _bitcoinNodeManager.broadcastBlockFinder(blockFinderHashes);
                    }
                    catch (final DatabaseException exception) {
                        Logger.log(exception);
                    }
                }

                synchronized (_mutex) {
                    Logger.log("+++ BlockChainBuilder - S");
                    if (_thread == Thread.currentThread()) {
                        Logger.log("+++ BlockChainBuilder - T");
                        _thread = null;
                    }
                }
            }
        };
    }

    public void start() {
        synchronized (_mutex) {
            if (_thread == null) {
                _startThread();
            }
        }
    }

    public void wakeUp() {
        _wasNotifiedOfNewBlock = true;

        synchronized (_mutex) {
            if (_thread == null) {
                _startThread();
            }
        }
    }

    public void stop() {
        _wasNotifiedOfNewBlock = false;

        final Thread thread;
        synchronized (_mutex) {
            thread = _thread;
            _thread = null;
        }

        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            }
            catch (final InterruptedException exception) { }
        }
    }
}

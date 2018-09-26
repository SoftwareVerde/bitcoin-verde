package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
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

public class BlockChainBuilder {
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected final Runnable _coreRunnable;
    protected final BlockProcessor _blockProcessor;

    protected volatile boolean _wasNotifiedOfNewBlock = false;
    protected Thread _thread = null;

    protected void _startThread() {
        _wasNotifiedOfNewBlock = true;
        _thread = new Thread(_coreRunnable);
        _thread.start();
    }

    protected void _processPendingBlockId(final PendingBlockId pendingBlockId, final MysqlDatabaseConnection databaseConnection, final PendingBlockDatabaseManager pendingBlockDatabaseManager) throws DatabaseException {
        final PendingBlock pendingBlock = pendingBlockDatabaseManager.getPendingBlock(pendingBlockId);
        final ByteArray blockData = pendingBlock.getData();

        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(blockData);

        if (block != null) {
            _blockProcessor.processBlock(block);
        }
        else {
            final Sha256Hash pendingBlockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
            Logger.log("NOTICE: Pending Block Corrupted: " + pendingBlockHash);
        }

        TransactionUtil.startTransaction(databaseConnection);
        pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);
        TransactionUtil.commitTransaction(databaseConnection);
    }

    public BlockChainBuilder(final BitcoinNodeManager bitcoinNodeManager, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache, final BlockProcessor blockProcessor) {
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;
        _blockProcessor = blockProcessor;

        _coreRunnable = new Runnable() {
            @Override
            public void run() {
                while (_wasNotifiedOfNewBlock) {
                    _wasNotifiedOfNewBlock = false;

                    try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection, _databaseCache);
                        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseCache);
                        final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);


                        BlockChainSegmentId targetBlockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();

                        while (true) {
                            final BlockId previousBlockId = blockChainDatabaseManager.getHeadBlockIdOfBlockChainSegment(targetBlockChainSegmentId);
                            final Sha256Hash previousBlockHash = blockDatabaseManager.getBlockHashFromId(previousBlockId);

                            final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(previousBlockHash);
                            if (pendingBlockIds.isEmpty()) {
                                _bitcoinNodeManager.requestBlockHashesAfter(previousBlockHash, null);
                                break;
                            }

                            for (final PendingBlockId pendingBlockId : pendingBlockIds) {
                                _processPendingBlockId(pendingBlockId, databaseConnection, pendingBlockDatabaseManager);
                            }
                        }

                        while (true) {
                            final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.selectCandidatePendingBlockId();
                            if (pendingBlockId == null) { break; }

                            _processPendingBlockId(pendingBlockId, databaseConnection, pendingBlockDatabaseManager);
                        }
                    }
                    catch (final DatabaseException exception) {
                        Logger.log(exception);
                        try { Thread.sleep(10000L); } catch (final InterruptedException interruptedException) { break; }
                    }
                }

                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseConnection, _databaseCache);
                    final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();
                    _bitcoinNodeManager.broadcastBlockFinder(blockFinderHashes);
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                }

                _thread = null;
            }
        };
    }

    public void start() {
        if (_thread != null) {
            _startThread();
        }
    }

    public void wakeUp() {
        _wasNotifiedOfNewBlock = true;

        if (_thread == null) {
            _startThread();
        }
    }

    public void stop() {
        _wasNotifiedOfNewBlock = false;

        if (_thread != null) {
            _thread.interrupt();
            try {
                _thread.join();
            }
            catch (final InterruptedException exception) { }

            _thread = null;
        }
    }
}

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
    protected final Object _mutex = new Object();

    protected volatile boolean _wasNotifiedOfNewBlock = false;
    protected Thread _thread = null;

    protected void _startThread() {
        _wasNotifiedOfNewBlock = true;
        final Thread thread = new Thread(_coreRunnable);
        _thread = thread;
        thread.start();
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

                        final BlockChainSegmentId targetBlockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();

                        while (true) {
                            final BlockId previousBlockId = blockChainDatabaseManager.getHeadBlockIdOfBlockChainSegment(targetBlockChainSegmentId);
                            final Sha256Hash previousBlockHash = blockDatabaseManager.getBlockHashFromId(previousBlockId);

Logger.log("********** BlockChainBuilder::getPendingBlockIdsWithPreviousBlockHash");
                            final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(previousBlockHash);
Logger.log("********** BlockChainBuilder::getPendingBlockIdsWithPreviousBlockHash2");
                            if (pendingBlockIds.isEmpty()) {
                                _bitcoinNodeManager.requestBlockHashesAfter(previousBlockHash);
                                break;
                            }

                            for (final PendingBlockId pendingBlockId : pendingBlockIds) {
Logger.log("********** BlockChainBuilder::_processPendingBlockId - Main Chain");
                                _processPendingBlockId(pendingBlockId, databaseConnection, pendingBlockDatabaseManager);
Logger.log("********** BlockChainBuilder::_processPendingBlockId - Main Chain2");
                            }
                        }

                        while (true) {
                            final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.selectCandidatePendingBlockId();
                            if (pendingBlockId == null) { break; }

Logger.log("********** BlockChainBuilder::_processPendingBlockId - Side Chain");
                            _processPendingBlockId(pendingBlockId, databaseConnection, pendingBlockDatabaseManager);
Logger.log("********** BlockChainBuilder::_processPendingBlockId - Side Chain2");
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

                synchronized (_mutex) {
                    if (_thread == Thread.currentThread()) {
                        _thread = null;
                    }
                }
            }
        };
    }

    public void start() {
Logger.log("********** BlockChainBuilder::start");
        synchronized (_mutex) {
            if (_thread == null) {
                _startThread();
            }
        }
Logger.log("********** BlockChainBuilder::start2");
    }

    public void wakeUp() {
Logger.log("********** BlockChainBuilder::wakeUp");
        _wasNotifiedOfNewBlock = true;

        synchronized (_mutex) {
            if (_thread == null) {
                _startThread();
            }
        }
Logger.log("********** BlockChainBuilder::wakeUp2");
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

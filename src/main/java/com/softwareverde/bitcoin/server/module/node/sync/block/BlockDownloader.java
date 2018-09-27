package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

import java.util.HashSet;
import java.util.Set;

public class BlockDownloader {
    protected static final Integer MAX_DOWNLOAD_FAILURE_COUNT = 10;
    protected static final Integer MAX_CONCURRENT_DOWNLOAD_COUNT = 8;

    protected final Object _synchronizer = new Object();

    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected final Set<PendingBlockId> _currentBlockDownloadSet = new HashSet<PendingBlockId>(MAX_CONCURRENT_DOWNLOAD_COUNT);
    protected final Runnable _coreRunnable;
    protected final BitcoinNode.DownloadBlockCallback _blockDownloadedCallback;

    protected volatile boolean _shouldContinue = false;
    protected Thread _thread = null;

    protected Runnable _newBlockAvailableCallback = null;

    protected void _startThread() {
        _shouldContinue = true;
        _thread = new Thread(_coreRunnable);
        _thread.start();
    }

    protected Integer _getCurrentConnectionCount(final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final java.util.List<Row> rows = databaseConnection.query(new Query("SHOW STATUS WHERE variable_name = 'Threads_connected'"));
        final Row row = rows.get(0);
        return row.getInteger("value");
    }

    public BlockDownloader(final BitcoinNodeManager bitcoinNodeManager, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache) {
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;

        _blockDownloadedCallback = new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                if (block == null) { return; }

                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
Logger.log("** ACTIVE DATABASE CONNECTION COUNT: "+ _getCurrentConnectionCount(databaseConnection));

                    final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

                    final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlock(block);
                    _currentBlockDownloadSet.remove(pendingBlockId);

                    synchronized (_synchronizer) {
                        _synchronizer.notifyAll();
                    }
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    return;
                }

                final Runnable newBlockAvailableCallback = _newBlockAvailableCallback;
                if (newBlockAvailableCallback != null) {
                    newBlockAvailableCallback.run();
                }
            }

            @Override
            public void onFailure(final Sha256Hash blockHash) {
                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

                    final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(blockHash);
                    if (pendingBlockId == null) { return; }

                    pendingBlockDatabaseManager.incrementFailedDownloadCount(pendingBlockId);
                    pendingBlockDatabaseManager.purgeFailedPendingBlocks(MAX_DOWNLOAD_FAILURE_COUNT);
                    _currentBlockDownloadSet.remove(pendingBlockId);

                    synchronized (_synchronizer) {
                        _synchronizer.notifyAll();
                    }
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                }
            }
        };

        _coreRunnable = new Runnable() {
            @Override
            public void run() {
                while (_shouldContinue) {

                    { // Determine if routine should wait for a request to complete...
                        final Integer currentDownloadCount = _currentBlockDownloadSet.size();
                        if (currentDownloadCount >= MAX_CONCURRENT_DOWNLOAD_COUNT) {
                            synchronized (_synchronizer) {
                                try {
                                    _synchronizer.wait();
                                }
                                catch (final InterruptedException exception) { break; }
                            }
                        }
                    }

                    try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                        final Integer currentDownloadCount = _currentBlockDownloadSet.size();
                        for (int i = 0; i < (MAX_CONCURRENT_DOWNLOAD_COUNT - currentDownloadCount); ++i) {
                            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
                            final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.selectIncompletePendingBlocks(MAX_CONCURRENT_DOWNLOAD_COUNT);
                            if (pendingBlockIds.isEmpty()) {
                                _shouldContinue = false;
                                break;
                            }

                            for (final PendingBlockId pendingBlockId : pendingBlockIds) {
                                final Boolean itemIsAlreadyBeingDownloaded = _currentBlockDownloadSet.contains(pendingBlockId);
                                if (itemIsAlreadyBeingDownloaded) { continue; }

                                _currentBlockDownloadSet.add(pendingBlockId);
                                final Sha256Hash blockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                                _bitcoinNodeManager.requestBlock(blockHash, _blockDownloadedCallback);
                                break;
                            }
                        }
                    }
                    catch (final DatabaseException exception) {
                        Logger.log(exception);
                        try { Thread.sleep(10000L); } catch (final InterruptedException interruptedException) { break; }
                    }
                }

                synchronized (_synchronizer) {
                    if (_thread == Thread.currentThread()) {
                        _thread = null;
                    }
                }
            }
        };
    }

    public void setNewBlockAvailableCallback(final Runnable runnable) {
        _newBlockAvailableCallback = runnable;
    }

    public void start() {
        synchronized (_synchronizer) {
            if (_thread == null) {
                _startThread();
            }
        }
    }

    public void wakeUp() {
        synchronized (_synchronizer) {
            _synchronizer.notifyAll();

            if (_thread == null) {
                _startThread();
            }
        }
    }

    public void stop() {
        _shouldContinue = false;
        final Thread thread = _thread;
        _thread = null;

        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            }
            catch (final InterruptedException exception) { }
        }
    }
}

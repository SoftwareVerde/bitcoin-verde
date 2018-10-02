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
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.Timer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockDownloader {
    protected static final Integer MAX_DOWNLOAD_FAILURE_COUNT = 10;

    public enum Status {
        DOWNLOADING, SLEEPING
    }

    public interface StatusMonitor {
        Status getStatus();
    }

    protected final Object _threadObjectMutex = new Object();
    protected final Object _downloadCallbackSynchronizer = new Object();

    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected final Map<PendingBlockId, Timer> _currentBlockDownloadSet = new ConcurrentHashMap<PendingBlockId, Timer>();
    protected final Runnable _coreRunnable;
    protected final BitcoinNode.DownloadBlockCallback _blockDownloadedCallback;
    protected final StatusMonitor _statusMonitor;

    protected volatile boolean _shouldContinue = false;
    protected Thread _thread = null;

    protected Runnable _newBlockAvailableCallback = null;

    protected void _startThread() {
        _shouldContinue = true;
        _thread = new Thread(_coreRunnable);
        _thread.start();
    }

    public BlockDownloader(final BitcoinNodeManager bitcoinNodeManager, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache) {
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;

        _statusMonitor = new StatusMonitor() {
            @Override
            public Status getStatus() {
                synchronized (_threadObjectMutex) {
                    if ((_thread == null) || (!_shouldContinue)) {
                        return Status.SLEEPING;
                    }
                    return Status.DOWNLOADING;
                }
            }
        };

        _blockDownloadedCallback = new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                if (block == null) { Logger.log("-- Downloader A"); return; }
                if (! _shouldContinue) { Logger.log("-- Downloader B"); return; }
                Logger.log("-- Downloader C");
                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

                    final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlock(block);
                    final Timer timer = _currentBlockDownloadSet.remove(pendingBlockId);
                    if (timer != null) {
                        timer.stop();
                    }

                    Logger.log("Downloaded Block: " + block.getHash() + " (" + (timer != null ? timer.getMillisecondsElapsed() : "??") + "ms)");

                    synchronized (_downloadCallbackSynchronizer) {
                        _downloadCallbackSynchronizer.notifyAll();
                    }
                }
                catch (final DatabaseException exception) {
                    Logger.log("-- Downloader D");
                    Logger.log(exception);
                    return;
                }

                final Runnable newBlockAvailableCallback = _newBlockAvailableCallback;
                if (newBlockAvailableCallback != null) {
                    newBlockAvailableCallback.run();
                }
                Logger.log("-- Downloader E");
            }

            @Override
            public void onFailure(final Sha256Hash blockHash) {
                if (! _shouldContinue) { Logger.log("-- Downloader F"); return; }

                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

                    final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(blockHash);
                    if (pendingBlockId == null) { Logger.log("-- Downloader G"); return; }

                    pendingBlockDatabaseManager.incrementFailedDownloadCount(pendingBlockId);
                    pendingBlockDatabaseManager.purgeFailedPendingBlocks(MAX_DOWNLOAD_FAILURE_COUNT);
                    _currentBlockDownloadSet.remove(pendingBlockId);

                    synchronized (_downloadCallbackSynchronizer) {
                        _downloadCallbackSynchronizer.notifyAll();
                    }
                    Logger.log("-- Downloader H");
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                }
            }
        };

        _coreRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Logger.log("-- Downloader I");
                    while (_shouldContinue) {
                        Logger.log("-- Downloader Z: " + _shouldContinue);
                        final Integer maximumConcurrentDownloadCount = Math.max(1, _bitcoinNodeManager.getActiveNodeCount());
                        Logger.log("-- Downloader J");

                        { // Determine if routine should wait for a request to complete...
                            final Integer currentDownloadCount = _currentBlockDownloadSet.size();
                            if (currentDownloadCount >= maximumConcurrentDownloadCount) {
                                synchronized (_downloadCallbackSynchronizer) {
                                    try {
                                        Logger.log("-- Downloader K");
                                        _downloadCallbackSynchronizer.wait();
                                        Logger.log("-- Downloader L");
                                    }
                                    catch (final InterruptedException exception) {
                                        Logger.log("-- Downloader M");
                                        break;
                                    }
                                }
                            }
                        }

                        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                            final Integer currentDownloadCount = _currentBlockDownloadSet.size();
                            // final Integer newDownloadCount = (maximumConcurrentDownloadCount - currentDownloadCount);

                            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
                            final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.selectIncompletePendingBlocks(maximumConcurrentDownloadCount);
                            if (pendingBlockIds.isEmpty()) {
                                Logger.log("-- Downloader N");
                                _shouldContinue = false;
                                break;
                            }
                            for (int i = 0; i < pendingBlockIds.getSize(); ++i) {
                                if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) {
                                    Logger.log("-- Downloader - Max Concurrent Reached: " + _currentBlockDownloadSet.size());
                                    final Set<PendingBlockId> concurrentPendingBlockIds = Util.copySet(_currentBlockDownloadSet.keySet());
                                    Logger.log(concurrentPendingBlockIds.size());
                                    for (final PendingBlockId pendingBlockId : concurrentPendingBlockIds) {
                                        final Timer timer = _currentBlockDownloadSet.get(pendingBlockId);
                                        if (timer == null) { Logger.log("-- Downloader - Timer - null"); }
                                        else {
                                            timer.stop();
                                            Logger.log("-- Downloader - Timer - " + timer.getMillisecondsElapsed());
                                        }
                                    }
                                    break;
                                }

                                Logger.log("-- Downloader X1");
                                final PendingBlockId pendingBlockId = pendingBlockIds.get(i);
                                final Boolean itemIsAlreadyBeingDownloaded = _currentBlockDownloadSet.containsKey(pendingBlockId);
                                Logger.log("-- Downloader X2");
                                if (itemIsAlreadyBeingDownloaded) {
                                    Logger.log("-- Downloader O");
                                    continue;
                                }
                                Logger.log("-- Downloader X3");

                                final Sha256Hash blockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                                if (blockHash == null) {
                                    Logger.log("-- Downloader P");
                                    continue;
                                }
                                Logger.log("-- Downloader X4");

                                final Timer timer = new Timer();
                                timer.start();
                                _currentBlockDownloadSet.put(pendingBlockId, timer);
                                Logger.log("-- Downloader X5");
                                _bitcoinNodeManager.requestBlock(blockHash, _blockDownloadedCallback);
                                Logger.log("-- Downloader X6");
                            }
                        }
                        catch (final DatabaseException exception) {
                            Logger.log(exception);
                            Logger.log("-- Downloader 3");
                            try { Thread.sleep(10000L); }
                            catch (final InterruptedException interruptedException) {
                                break;
                            }
                            Logger.log("-- Downloader 2");
                        }

                        Logger.log("-- Downloader 1");
                    }
                }
                catch (final Exception exception) {
                    Logger.log(exception);
                }

                Logger.log("-- Downloader Q");
                synchronized (_threadObjectMutex) {
                    if (_thread == Thread.currentThread()) {
                        Logger.log("-- Downloader R");
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
        synchronized (_threadObjectMutex) {
            if (_thread == null) {
                _currentBlockDownloadSet.clear();
                _startThread();
            }
        }
    }

    public void wakeUp() {
        // Logger.log("-- Downloader S");
        synchronized (_threadObjectMutex) {
            if (_thread == null) {
                Logger.log("-- Downloader T");
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

    public StatusMonitor getStatusMonitor() {
        return _statusMonitor;
    }
}

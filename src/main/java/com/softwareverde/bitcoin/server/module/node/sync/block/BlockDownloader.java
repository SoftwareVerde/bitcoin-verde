package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.SleepyService;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.timer.MilliTimer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockDownloader extends SleepyService {
    protected static final Long MAX_TIMEOUT = 90000L;
    protected static final Integer MAX_DOWNLOAD_FAILURE_COUNT = 10;

    protected final Object _downloadCallbackPin = new Object();

    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected final Map<PendingBlockId, MilliTimer> _currentBlockDownloadSet = new ConcurrentHashMap<PendingBlockId, MilliTimer>();
    protected final BitcoinNodeManager.DownloadBlockCallback _blockDownloadedCallback;

    protected Runnable _newBlockAvailableCallback = null;

    protected void _onBlockDownloaded(final Block block, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

        final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.storeBlock(block);
        final MilliTimer timer = _currentBlockDownloadSet.remove(pendingBlockId);
        if (timer != null) {
            timer.stop();
        }

        Logger.log("Downloaded Block: " + block.getHash() + " (" + (timer != null ? timer.getMillisecondsElapsed() : "??") + "ms)");

        synchronized (_downloadCallbackPin) {
            _downloadCallbackPin.notifyAll();
        }
    }

    protected void _markPendingBlockIdsAsFailed(final Set<PendingBlockId> pendingBlockIds) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            for (final PendingBlockId pendingBlockId : pendingBlockIds) {
                pendingBlockDatabaseManager.incrementFailedDownloadCount(pendingBlockId);
            }
            pendingBlockDatabaseManager.purgeFailedPendingBlocks(MAX_DOWNLOAD_FAILURE_COUNT);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        final Integer maximumConcurrentDownloadCount = Math.max(1, _bitcoinNodeManager.getActiveNodeCount());

        { // Determine if routine should wait for a request to complete...
            Integer currentDownloadCount = _currentBlockDownloadSet.size();
            while (currentDownloadCount >= maximumConcurrentDownloadCount) {
                synchronized (_downloadCallbackPin) {
                    final MilliTimer waitTimer = new MilliTimer();
                    try {
                        waitTimer.start();
                        _downloadCallbackPin.wait(MAX_TIMEOUT);
                        waitTimer.stop();
                    }
                    catch (final InterruptedException exception) { return false; }

                    if (waitTimer.getMillisecondsElapsed() > MAX_TIMEOUT) {
                        Logger.log("NOTICE: Block download stalled.");

                        _markPendingBlockIdsAsFailed(_currentBlockDownloadSet.keySet());
                        _currentBlockDownloadSet.clear();
                        return false;
                    }
                }

                currentDownloadCount = _currentBlockDownloadSet.size();
            }
        }

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.selectIncompletePendingBlocks(maximumConcurrentDownloadCount * 2);
            if (pendingBlockIds.isEmpty()) { return false; }

            for (int i = 0; i < pendingBlockIds.getSize(); ++i) {
                if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) { break; }

                final PendingBlockId pendingBlockId = pendingBlockIds.get(i);

                final Boolean itemIsAlreadyBeingDownloaded = _currentBlockDownloadSet.containsKey(pendingBlockId);
                if (itemIsAlreadyBeingDownloaded) { continue; }

                final Sha256Hash blockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                if (blockHash == null) { continue; }

                final MilliTimer timer = new MilliTimer();
                _currentBlockDownloadSet.put(pendingBlockId, timer);

                timer.start();
                _bitcoinNodeManager.requestBlock(blockHash, _blockDownloadedCallback);
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }

        return true;
    }

    @Override
    protected void _onSleep() { }

    public BlockDownloader(final BitcoinNodeManager bitcoinNodeManager, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache) {
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;

        _blockDownloadedCallback = new BitcoinNodeManager.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    _onBlockDownloaded(block, databaseConnection);
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
                    if (pendingBlockId == null) {
                        Logger.log("Unable to increment download failure count for block: " + blockHash);
                        return;
                    }

                    _currentBlockDownloadSet.remove(pendingBlockId);

                    pendingBlockDatabaseManager.incrementFailedDownloadCount(pendingBlockId);
                    pendingBlockDatabaseManager.purgeFailedPendingBlocks(MAX_DOWNLOAD_FAILURE_COUNT);
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    Logger.log("Unable to increment download failure count for block: " + blockHash);
                }

                synchronized (_downloadCallbackPin) {
                    _downloadCallbackPin.notifyAll();
                }
            }
        };
    }

    public void setNewBlockAvailableCallback(final Runnable runnable) {
        _newBlockAvailableCallback = runnable;
    }

}

package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;
import com.softwareverde.bitcoin.server.module.node.sync.blockqueue.BlockQueue;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.Timer;

public class BlockSynchronizer {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final BitcoinNodeManager _nodeManager;

    protected Integer _maxQueueSize = 1;
    protected final BlockQueue _queuedBlocks = new BlockQueue();
    protected final BlockProcessor _blockProcessor;
    protected final BlockValidatorThread _blockValidatorThread;
    protected volatile Boolean _shouldContinue = false;
    protected Boolean _isRunning = false;

    protected final Object _mutex = new Object();
    protected Sha256Hash _lastBlockHash;
    protected final MutableList<Sha256Hash> _queuedBlockHashes = new MutableList<Sha256Hash>();
    protected final BitcoinNode.DownloadBlockCallback _downloadBlockCallback;
    protected final BitcoinNode.QueryCallback _getBlocksHashesAfterCallback;

    protected final Timer _downloadBlockTimer = new Timer();

    protected Boolean _hasGenesisBlock() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
            final Sha256Hash lastKnownHash = blockDatabaseManager.getHeadBlockHash();
            return (lastKnownHash != null);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }
    }

    protected Sha256Hash _getHeadBlockHash() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
            final Sha256Hash headBlockHash = blockDatabaseManager.getHeadBlockHash();
            return Util.coalesce(headBlockHash, Block.GENESIS_BLOCK_HASH);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    protected void _onBlockHashesDownloaded(final List<Sha256Hash> blockHashes) {
        if (! _shouldContinue) {
            _isRunning = false;
            return;
        }

        final Sha256Hash nextBlockHash;
        synchronized (_mutex) {
            _queuedBlockHashes.addAll(blockHashes);
            nextBlockHash = ( (_queuedBlockHashes.isEmpty()) ? null : _queuedBlockHashes.remove(0) );
        }

        if (nextBlockHash != null) {
            _downloadBlockTimer.start();
            _nodeManager.requestBlock(nextBlockHash, _downloadBlockCallback);
        }
        else {
            _onFailure();
        }
    }

    protected void _onBlockDownloaded(final Block block) {
        if (! _shouldContinue) {
            _isRunning = false;
            return;
        }

        _downloadBlockTimer.stop();

        final Sha256Hash blockHash = block.getHash();
        final Sha256Hash previousBlockHash = block.getPreviousBlockHash();

        Logger.log("DOWNLOADED BLOCK: "+ blockHash + " ("+ _downloadBlockTimer.getMillisecondsElapsed() +"ms)");

        final Sha256Hash nextBlockHash;
        final Sha256Hash lastBlockHash;
        synchronized (_mutex) {
            if (! Util.areEqual(_lastBlockHash, previousBlockHash)) { return; } // Ignore blocks sent out of order... NOTE: BlockSynchronizer::_restartBlockDownload depends on this logic.

            _queuedBlocks.addBlock(block);

            Logger.log("Block Queue Size: " + _queuedBlocks.getSize() + " / " + _maxQueueSize);

            _lastBlockHash = blockHash;

            while (_queuedBlocks.getSize() >= _maxQueueSize) {
                if (! _shouldContinue) {
                    _isRunning = false;
                    return;
                }

                try {
                    Thread.sleep(500L);
                }
                catch (final InterruptedException exception) {
                    _onFailure();
                    return;
                }
            }

            nextBlockHash = ( (_queuedBlockHashes.isEmpty()) ? null : _queuedBlockHashes.remove(0) );
            lastBlockHash = _lastBlockHash;
        }

        if (nextBlockHash != null) {
            _downloadBlockTimer.start();
            _nodeManager.requestBlock(nextBlockHash, _downloadBlockCallback);
        }
        else if (lastBlockHash != null) {
            _nodeManager.requestBlockHashesAfter(lastBlockHash, _getBlocksHashesAfterCallback);
        }
        else {
            _onFailure();
        }
    }

    protected void _clear() {
        synchronized (_mutex) {
            _queuedBlockHashes.clear();
            _queuedBlocks.clear();
            _lastBlockHash = null;
        }
    }

    protected void _startDownloadingBlocks() {
        final Sha256Hash lastKnownHash = _getHeadBlockHash();

        synchronized (_mutex) {
            _lastBlockHash = lastKnownHash;
        }

        if (_lastBlockHash != null) {
            _nodeManager.requestBlockHashesAfter(_lastBlockHash, _getBlocksHashesAfterCallback);
        }
        else {
            _onFailure();
        }
    }

    protected void _restartBlockDownload() {
        // NOTE: Any currently-pending requests will be completed but ignored since BlockSynchronizer::_clear resets BlockSynchronizer._lastBlockHash...
        _clear();
        _startDownloadingBlocks();
    }

    protected void _onFailure() {
        _isRunning = false;
        _shouldContinue = false;

        _clear();
    }

    public BlockSynchronizer(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final BitcoinNodeManager nodeManager, final BlockProcessor blockProcessor) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _nodeManager = nodeManager;
        _blockProcessor = blockProcessor;

        final BlockValidatorThread.InvalidBlockCallback invalidBlockHandler = new BlockValidatorThread.InvalidBlockCallback() {
            @Override
            public void onInvalidBlock(final Block invalidBlock) {
                _restartBlockDownload();
            }
        };

        _downloadBlockCallback = new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                _onBlockDownloaded(block);
            }

            @Override
            public void onFailure() {
                _onFailure();
            }
        };

        _getBlocksHashesAfterCallback = new BitcoinNode.QueryCallback() {
            @Override
            public void onResult(final List<Sha256Hash> blockHashes) {
                _onBlockHashesDownloaded(blockHashes);
            }

            @Override
            public void onFailure() {
                _onFailure();
            }
        };

        _blockValidatorThread = new BlockValidatorThread(_queuedBlocks, _blockProcessor, invalidBlockHandler);
    }

    public void setMaxQueueSize(final Integer maxQueueSize) {
        _maxQueueSize = maxQueueSize;
    }

    public void start() {
        if (_isRunning && _shouldContinue) { return; }

        _isRunning = true;
        _shouldContinue = true;
        _blockValidatorThread.start();

        if (! _hasGenesisBlock()) {
            _nodeManager.requestBlock(Block.GENESIS_BLOCK_HASH, new BitcoinNode.DownloadBlockCallback() {
                @Override
                public void onResult(final Block block) {
                    if (! _hasGenesisBlock()) {
                        // NOTE: This can happen if the NodeModule received GenesisBlock from another process...
                        final Boolean isValidBlock = _blockProcessor.processBlock(block);
                        if (! isValidBlock) {
                            Logger.error("Error processing genesis block.");
                            return;
                        }
                    }

                    _startDownloadingBlocks();
                }

                @Override
                public void onFailure() {
                    _isRunning = false;
                    _shouldContinue = false;
                }
            });
        }
        else {
            _startDownloadingBlocks();
        }
    }

    public void stop() {
        _clear();
        _shouldContinue = false;
        _blockValidatorThread.stop();
    }

    public Boolean isRunning() {
        return (_isRunning && _shouldContinue);
    }

    public JsonRpcSocketServerHandler.StatisticsContainer getStatisticsContainer() {
        return _blockProcessor.getStatisticsContainer();
    }

    public void submitBlock(final Block block) {
        _queuedBlocks.addBlock(block);
    }
}

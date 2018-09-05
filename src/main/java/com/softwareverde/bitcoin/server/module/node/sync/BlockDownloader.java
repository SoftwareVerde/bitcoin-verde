package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;

import java.util.concurrent.ConcurrentLinkedQueue;

public class BlockDownloader {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final BitcoinNodeManager _nodeManager;

    protected Integer _maxQueueSize = 1;
    protected final ConcurrentLinkedQueue<Block> _queuedBlocks = new ConcurrentLinkedQueue<Block>();
    protected final BlockProcessor _blockProcessor;
    protected final BlockValidatorThread _blockValidatorThread;
    protected volatile Boolean _shouldContinue = false;
    protected Boolean _isRunning = false;

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

    public BlockDownloader(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final BitcoinNodeManager nodeManager, final BlockProcessor blockProcessor) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _nodeManager = nodeManager;
        _blockProcessor = blockProcessor;

        _blockValidatorThread = new BlockValidatorThread(_queuedBlocks, _blockProcessor);
    }

    public void setMaxQueueSize(final Integer maxQueueSize) {
        _maxQueueSize = maxQueueSize;
    }

    protected void _downloadAllBlocks() {
        final Sha256Hash resumeAfterHash;
        {
            Sha256Hash lastKnownHash = null;
            try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                lastKnownHash = blockDatabaseManager.getHeadBlockHash();
            }
            catch (final DatabaseException e) { }

            resumeAfterHash = Util.coalesce(lastKnownHash, Block.GENESIS_BLOCK_HASH);
        }

        final Container<Sha256Hash> lastBlockHash = new Container<Sha256Hash>(resumeAfterHash);
        final Container<BitcoinNode.QueryCallback> getBlocksHashesAfterCallback = new Container<BitcoinNode.QueryCallback>();

        final MutableList<Sha256Hash> availableBlockHashes = new MutableList<Sha256Hash>();

        final BitcoinNode.DownloadBlockCallback downloadBlockCallback = new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                if (! _shouldContinue) {
                    _isRunning = false;
                    return;
                }

                Logger.log("DOWNLOADED BLOCK: "+ block.getHash());

                if (! lastBlockHash.value.equals(block.getPreviousBlockHash())) { return; } // Ignore blocks sent out of order...

                _queuedBlocks.add(block);
                Logger.log("Block Queue Size: "+ _queuedBlocks.size() + " / " + _maxQueueSize);

                lastBlockHash.value = block.getHash();

                while (_queuedBlocks.size() >= _maxQueueSize) {
                    if (! _shouldContinue) {
                        _isRunning = false;
                        return;
                    }

                    try { Thread.sleep(500L); } catch (final Exception exception) { return; }
                }

                if (! availableBlockHashes.isEmpty()) {
                    _nodeManager.requestBlock(availableBlockHashes.remove(0), this);
                }
                else {
                    _nodeManager.requestBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
                }
            }

            @Override
            public void onFailure() {
                _isRunning = false;
                _shouldContinue = false;
            }
        };

        getBlocksHashesAfterCallback.value = new BitcoinNode.QueryCallback() {
            @Override
            public void onResult(final List<Sha256Hash> blockHashes) {
                if (! _shouldContinue) {
                    _isRunning = false;
                    return;
                }

                availableBlockHashes.addAll(blockHashes);

                if (! availableBlockHashes.isEmpty()) {
                    _nodeManager.requestBlock(availableBlockHashes.remove(0), downloadBlockCallback);
                }
            }

            @Override
            public void onFailure() {
                _isRunning = false;
                _shouldContinue = false;
            }
        };

        _nodeManager.requestBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
    }

    public void start() {
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

                    _downloadAllBlocks();
                }

                @Override
                public void onFailure() {
                    _isRunning = false;
                    _shouldContinue = false;
                }
            });
        }
        else {
            _downloadAllBlocks();
        }
    }

    public void stop() {
        _shouldContinue = false;
        _blockValidatorThread.stop();
    }

    public Boolean isRunning() {
        return (_isRunning && _shouldContinue);
    }

    public JsonRpcSocketServerHandler.StatisticsContainer getStatisticsContainer() {
        return _blockProcessor.getStatisticsContainer();
    }
}

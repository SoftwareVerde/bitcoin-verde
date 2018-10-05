package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.SleepyService;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.manager.ThreadPool;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

public class BlockHeaderDownloader extends SleepyService {
    public static final Long MAX_TIMEOUT_MS = 60000L;

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;
    protected final BitcoinNodeManager _nodeManager;
    protected final MutableMedianBlockTime _medianBlockTime;
    protected final BlockDownloadRequester _blockDownloadRequester;
    protected final ThreadPool _threadPool = new ThreadPool(0, 2, 60000L);
    protected final MilliTimer _timer;
    protected final BitcoinNodeManager.DownloadBlockHeadersCallback _downloadBlockHeadersCallback;
    protected final Container<Float> _averageBlockHeadersPerSecond = new Container<Float>(0F);

    protected final Object _headersDownloadedPin = new Object();
    protected final Object _genesisBlockPin = new Object();
    protected Boolean _hasGenesisBlock = false;

    protected Long _blockHeight = 0L;
    protected Sha256Hash _lastBlockHash = Block.GENESIS_BLOCK_HASH;
    protected Long _blockHeaderCount = 0L;

    protected Runnable _newBlockHeaderAvailableCallback = null;

    protected Boolean _checkForGenesisBlockHeader() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
            final Sha256Hash lastKnownHash = blockDatabaseManager.getHeadBlockHeaderHash();

            synchronized (_genesisBlockPin) {
                _hasGenesisBlock = (lastKnownHash != null);
                _genesisBlockPin.notifyAll();
            }

            return _hasGenesisBlock;
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }
    }

    protected void _downloadGenesisBlock() {
        final Runnable retry = new Runnable() {
            @Override
            public void run() {
                try { Thread.sleep(5000L); } catch (final InterruptedException exception) { return; }
                _downloadGenesisBlock();
            }
        };

        _nodeManager.requestBlock(Block.GENESIS_BLOCK_HASH, new BitcoinNodeManager.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                final Sha256Hash blockHash = block.getHash();
                Logger.log("GENESIS RECEIVED: " + blockHash);
                if (_checkForGenesisBlockHeader()) { return; } // NOTE: This can happen if the BlockDownloader received the GenesisBlock first...

                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final Boolean genesisBlockWasStored = _validateAndStoreBlockHeader(block, databaseConnection);
                    if (! genesisBlockWasStored) {
                        _threadPool.execute(retry);
                        return;
                    }

                    Logger.log("GENESIS STORED: " + block.getHash());

                    synchronized (_genesisBlockPin) {
                        _hasGenesisBlock = true;
                        _genesisBlockPin.notifyAll();
                    }
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    _threadPool.execute(retry);
                }
            }

            @Override
            public void onFailure(final Sha256Hash blockHash) {
                _threadPool.execute(retry);
            }
        });
    }

    protected Boolean _validateAndStoreBlockHeader(final BlockHeader blockHeader, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final Sha256Hash blockHash = blockHeader.getHash();

        if (! blockHeader.isValid()) {
            Logger.log("Invalid BlockHeader: " + blockHash);
            return false;
        }

        final BlockHeaderValidator blockValidator = new BlockHeaderValidator(databaseConnection, _databaseManagerCache, _nodeManager.getNetworkTime(), _medianBlockTime);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

        TransactionUtil.startTransaction(databaseConnection);

        final BlockId blockId = blockDatabaseManager.storeBlockHeader(BlockDatabaseManager.MUTEX, blockHeader);

        if (blockId == null) {
            Logger.log("Error storing BlockHeader: " + blockHash);
            TransactionUtil.rollbackTransaction(databaseConnection);
            return false;
        }

        final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);
        final Boolean blockHeaderIsValid = blockValidator.validateBlockHeader(blockChainSegmentId, blockHeader);
        if (! blockHeaderIsValid) {
            Logger.log("Invalid BlockHeader: " + blockHash);
            TransactionUtil.rollbackTransaction(databaseConnection);
            return false;
        }

        final Long blockHeight = blockDatabaseManager.getBlockHeightForBlockId(blockId);
        _blockHeight = Math.max(blockHeight, _blockHeight);

        TransactionUtil.commitTransaction(databaseConnection);
        return true;
    }

    protected void _processBlockHeaders(final List<BlockHeaderWithTransactionCount> blockHeaders) {
        final MilliTimer storeHeadersTimer = new MilliTimer();
        storeHeadersTimer.start();

        final BlockHeader firstBlockHeader = blockHeaders.get(0);
        Logger.log("DOWNLOADED BLOCK HEADERS: "+ firstBlockHeader.getHash() + " + " + blockHeaders.getSize());

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            for (final BlockHeader blockHeader : blockHeaders) {
                final Sha256Hash blockHash = blockHeader.getHash();

                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
                final Boolean blockAlreadyExists = blockDatabaseManager.blockHeaderExists(blockHash);
                if (! blockAlreadyExists) {
                    final Boolean blockHeaderWasStored = _validateAndStoreBlockHeader(blockHeader, databaseConnection);
                    if (! blockHeaderWasStored) { continue; }

                    _threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            _blockDownloadRequester.requestBlock(blockHeader);
                        }
                    });

                    _blockHeaderCount += 1L;
                    _timer.stop();
                    final Long millisecondsElapsed = _timer.getMillisecondsElapsed();
                    _averageBlockHeadersPerSecond.value = ( (_blockHeaderCount.floatValue() / millisecondsElapsed) * 1000L );
                }

                _lastBlockHash = blockHash;
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            Logger.log("Processing BlockHeaders failed.");
            return;
        }

        storeHeadersTimer.stop();
        Logger.log("Stored Block Headers: " + firstBlockHeader.getHash() + " - " + _lastBlockHash + " (" + storeHeadersTimer.getMillisecondsElapsed() + "ms)");
    }

    public BlockHeaderDownloader(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final BitcoinNodeManager nodeManager, final MutableMedianBlockTime medianBlockTime, final BlockDownloadRequester blockDownloadRequester) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
        _nodeManager = nodeManager;
        _medianBlockTime = medianBlockTime;
        _blockDownloadRequester = blockDownloadRequester;
        _timer = new MilliTimer();

        _downloadBlockHeadersCallback = new BitcoinNodeManager.DownloadBlockHeadersCallback() {
            @Override
            public void onResult(final List<BlockHeaderWithTransactionCount> blockHeaders) {
                _processBlockHeaders(blockHeaders);

                final Runnable newBlockHeaderAvailableCallback = _newBlockHeaderAvailableCallback;
                if (newBlockHeaderAvailableCallback != null) {
                    _threadPool.execute(newBlockHeaderAvailableCallback);
                }

                synchronized (_headersDownloadedPin) {
                    _headersDownloadedPin.notifyAll();
                }
            }

            @Override
            public void onFailure() {
                // Let the headersDownloadedPin timeout...
            }
        };
    }

    @Override
    protected void _onStart() {
        _timer.start();
        _blockHeaderCount = 0L;

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            if (headBlockId != null) {
                final Sha256Hash headBlockHash = blockDatabaseManager.getBlockHashFromId(headBlockId);
                _lastBlockHash = headBlockHash;
                _blockHeight = blockDatabaseManager.getBlockHeightForBlockId(headBlockId);
            }
            else {
                _lastBlockHash = Block.GENESIS_BLOCK_HASH;
                _blockHeight = 0L;
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            _lastBlockHash = Util.coalesce(_lastBlockHash, Block.GENESIS_BLOCK_HASH);
        }

        if (! _checkForGenesisBlockHeader()) {
            _downloadGenesisBlock();
        }
    }

    @Override
    protected Boolean _run() {
        synchronized (_genesisBlockPin) {
            while (! _hasGenesisBlock) {
                try { _genesisBlockPin.wait(); }
                catch (final InterruptedException exception) { return false; }
            }
        }

        _nodeManager.requestBlockHeadersAfter(_lastBlockHash, _downloadBlockHeadersCallback);

        synchronized (_headersDownloadedPin) {
            final MilliTimer timer = new MilliTimer();
            timer.start();

            try { _headersDownloadedPin.wait(MAX_TIMEOUT_MS); }
            catch (final InterruptedException exception) { return false; }

            timer.stop();
            if (timer.getMillisecondsElapsed() >= MAX_TIMEOUT_MS) { return false; }
        }

        return true;
    }

    @Override
    protected void _onSleep() { }

    public void setNewBlockHeaderAvailableCallback(final Runnable newBlockHeaderAvailableCallback) {
        _newBlockHeaderAvailableCallback = newBlockHeaderAvailableCallback;
    }

    public Container<Float> getAverageBlockHeadersPerSecondContainer() {
        return _averageBlockHeadersPerSecond;
    }

    public Long getBlockHeight() {
        return _blockHeight;
    }
}

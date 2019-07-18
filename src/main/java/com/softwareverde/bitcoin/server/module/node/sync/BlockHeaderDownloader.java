package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.BlockValidatorFactory;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

public class BlockHeaderDownloader extends SleepyService {
    public static final Long MAX_TIMEOUT_MS = (15L * 1000L); // 15 Seconds...

    protected final SystemTime _systemTime = new SystemTime();
    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final BitcoinNodeManager _nodeManager;
    protected final BlockValidatorFactory _blockValidatorFactory;
    protected final MutableMedianBlockTime _medianBlockTime;
    protected final BlockDownloadRequester _blockDownloadRequester;
    protected final ThreadPool _threadPool;
    protected final MilliTimer _timer;
    protected final BitcoinNodeManager.DownloadBlockHeadersCallback _downloadBlockHeadersCallback;
    protected final Container<Float> _averageBlockHeadersPerSecond = new Container<Float>(0F);

    protected final Object _headersDownloadedPin = new Object();
    protected final Object _genesisBlockPin = new Object();
    protected Boolean _hasGenesisBlock = false;

    protected Integer _maxHeaderBatchSize = 2000;

    protected Long _blockHeight = 0L;
    protected Sha256Hash _lastBlockHash = BlockHeader.GENESIS_BLOCK_HASH;
    protected BlockHeader _lastBlockHeader = null;
    protected Long _minBlockTimestamp = (_systemTime.getCurrentTimeInSeconds() - 3600L); // Default to an hour ago...
    protected Long _blockHeaderCount = 0L;

    protected Runnable _newBlockHeaderAvailableCallback = null;

    protected Boolean _checkForGenesisBlockHeader() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final Sha256Hash lastKnownHash = blockHeaderDatabaseManager.getHeadBlockHeaderHash();

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

                try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    final Boolean genesisBlockWasStored = _validateAndStoreBlockHeader(block, databaseManager);
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

    protected Boolean _validateAndStoreBlockHeader(final BlockHeader blockHeader, final DatabaseManager databaseManager) throws DatabaseException {
        final Sha256Hash blockHash = blockHeader.getHash();

        if (! blockHeader.isValid()) {
            Logger.log("Invalid BlockHeader: " + blockHash);
            return false;
        }

        final BlockHeaderValidator blockValidator = _blockValidatorFactory.newBlockHeaderValidator(databaseManager, _nodeManager.getNetworkTime(), _medianBlockTime);
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            TransactionUtil.startTransaction(databaseConnection);
            final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);

            if (blockId == null) {
                Logger.log("Error storing BlockHeader: " + blockHash);
                TransactionUtil.rollbackTransaction(databaseConnection);
                return false;
            }

            final BlockHeaderValidator.BlockHeaderValidationResponse blockHeaderValidationResponse = blockValidator.validateBlockHeader(blockHeader);
            if (! blockHeaderValidationResponse.isValid) {
                Logger.log("Invalid BlockHeader: " + blockHeaderValidationResponse.errorMessage + " (" + blockHash + ")");
                TransactionUtil.rollbackTransaction(databaseConnection);
                return false;
            }

            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            _blockHeight = Math.max(blockHeight, _blockHeight);

            TransactionUtil.commitTransaction(databaseConnection);
        }

        return true;
    }

    protected Boolean _validateAndStoreBlockHeaders(final List<BlockHeader> blockHeaders, final DatabaseManager databaseManager) throws DatabaseException {
        if (blockHeaders.isEmpty()) { return true; }

        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            { // Validate blockHeaders are sequential...
                final BlockHeader firstBlockHeader = blockHeaders.get(0);
                if (! firstBlockHeader.isValid()) { return false; }

                final BlockId previousBlockId = blockHeaderDatabaseManager.getBlockHeaderId(firstBlockHeader.getPreviousBlockHash());
                final Boolean previousBlockExists = (previousBlockId != null);
                if (! previousBlockExists) {
                    final Boolean isGenesisBlock = Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, firstBlockHeader.getHash());
                    if (! isGenesisBlock) { return false; }
                }
                else {
                    final Boolean hasChildren = blockHeaderDatabaseManager.hasChildBlock(previousBlockId);
                    if (hasChildren) {
                        // BlockHeaders cannot be batched due to potential forks...
                        for (final BlockHeader blockHeader : blockHeaders) {
                            final Boolean isValid = _validateAndStoreBlockHeader(blockHeader, databaseManager);
                            if (! isValid) { return false; }
                        }
                        return true;
                    }
                }
                Sha256Hash previousBlockHash = firstBlockHeader.getPreviousBlockHash();
                for (final BlockHeader blockHeader : blockHeaders) {
                    if (! blockHeader.isValid()) { return false; }
                    if (! Util.areEqual(previousBlockHash, blockHeader.getPreviousBlockHash())) {
                        return false;
                    }
                    previousBlockHash = blockHeader.getHash();
                }
            }

            final BlockHeaderValidator blockValidator = _blockValidatorFactory.newBlockHeaderValidator(databaseManager, _nodeManager.getNetworkTime(), _medianBlockTime);

            TransactionUtil.startTransaction(databaseConnection);
            final List<BlockId> blockIds = blockHeaderDatabaseManager.insertBlockHeaders(blockHeaders, _maxHeaderBatchSize);
            if (blockIds == null) {
                TransactionUtil.rollbackTransaction(databaseConnection);
                return false;
            }

            for (final BlockHeader blockHeader : blockHeaders) {
                final Sha256Hash blockHash = blockHeader.getHash();

                final BlockHeaderValidator.BlockHeaderValidationResponse blockHeaderValidationResponse = blockValidator.validateBlockHeader(blockHeader);
                if (!blockHeaderValidationResponse.isValid) {
                    Logger.log("Invalid BlockHeader: " + blockHeaderValidationResponse.errorMessage + " (" + blockHash + ")");
                    TransactionUtil.rollbackTransaction(databaseConnection);
                    return false;
                }
            }

            final BlockId lastBlockId = blockIds.get(blockIds.getSize() - 1);
            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(lastBlockId);
            _blockHeight = Math.max(blockHeight, _blockHeight);

            TransactionUtil.commitTransaction(databaseConnection);

            return true;
        }
    }

    protected void _processBlockHeaders(final List<BlockHeader> blockHeaders) {
        final MilliTimer storeHeadersTimer = new MilliTimer();
        storeHeadersTimer.start();

        final BlockHeader firstBlockHeader = blockHeaders.get(0);
        Logger.log("DOWNLOADED BLOCK HEADERS: "+ firstBlockHeader.getHash() + " + " + blockHeaders.getSize());

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            for (final BlockHeader blockHeader : blockHeaders) {
                final Sha256Hash blockHash = blockHeader.getHash();

                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                final Boolean blockAlreadyExists = blockHeaderDatabaseManager.blockHeaderExists(blockHash);
                if (! blockAlreadyExists) {
                    final Boolean blockHeaderWasStored = _validateAndStoreBlockHeader(blockHeader, databaseManager);
                    if (! blockHeaderWasStored) { continue; }

                    _threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (_blockDownloadRequester != null) {
                                _blockDownloadRequester.requestBlock(blockHeader);
                            }
                        }
                    });

                    _blockHeaderCount += 1L;
                    _timer.stop();
                    final Long millisecondsElapsed = _timer.getMillisecondsElapsed();
                    _averageBlockHeadersPerSecond.value = ( (_blockHeaderCount.floatValue() / millisecondsElapsed) * 1000L );
                }

                _lastBlockHash = blockHash;
                _lastBlockHeader = blockHeader;
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

    public BlockHeaderDownloader(final DatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager nodeManager, final BlockValidatorFactory blockValidatorFactory, final MutableMedianBlockTime medianBlockTime, final BlockDownloadRequester blockDownloadRequester, final ThreadPool threadPool) {
        _databaseManagerFactory = databaseManagerFactory;
        _nodeManager = nodeManager;
        _blockValidatorFactory = blockValidatorFactory;
        _medianBlockTime = medianBlockTime;
        _blockDownloadRequester = blockDownloadRequester;
        _timer = new MilliTimer();
        _threadPool = threadPool;

        _downloadBlockHeadersCallback = new BitcoinNodeManager.DownloadBlockHeadersCallback() {
            @Override
            public void onResult(final List<BlockHeader> blockHeaders) {
                final int blockHeadersCount = blockHeaders.getSize();

                int lastBatchIndex = 0;
                while (lastBatchIndex < blockHeadersCount) {

                    final List<BlockHeader> blockHeadersBatch;
                    {
                        if (_maxHeaderBatchSize >= blockHeadersCount) {
                            blockHeadersBatch = blockHeaders;
                            lastBatchIndex = blockHeadersCount;
                        }
                        else {
                            final MutableList<BlockHeader> batch = new MutableList<BlockHeader>(_maxHeaderBatchSize);
                            for (int i = 0; i < _maxHeaderBatchSize; ++i) {
                                if (lastBatchIndex >= blockHeadersCount) { break; }

                                final BlockHeader blockHeader = blockHeaders.get(lastBatchIndex);
                                batch.add(blockHeader);
                                lastBatchIndex += 1;
                            }
                            blockHeadersBatch = batch;
                        }
                    }

                    _processBlockHeaders(blockHeadersBatch);

                    final Runnable newBlockHeaderAvailableCallback = _newBlockHeaderAvailableCallback;
                    if (newBlockHeaderAvailableCallback != null) {
                        _threadPool.execute(newBlockHeaderAvailableCallback);
                    }
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

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            if (headBlockId != null) {
                final Sha256Hash headBlockHash = blockHeaderDatabaseManager.getBlockHash(headBlockId);
                _lastBlockHash = headBlockHash;
                _blockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
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
            if (timer.getMillisecondsElapsed() >= MAX_TIMEOUT_MS) {
                // The lastBlockHeader may be null when first starting.
                // If the first batch fails, then allow for the Downloader to sleep, regardless of minBlockHeight.
                if (_lastBlockHeader == null) { return false; }

                // Don't sleep after a timeout while the most recent block timestamp is less than the minBlockTimestamp...
                return (_lastBlockHeader.getTimestamp() < _minBlockTimestamp);
            }
        }

        return true;
    }

    @Override
    protected void _onSleep() { }

    public void setNewBlockHeaderAvailableCallback(final Runnable newBlockHeaderAvailableCallback) {
        _newBlockHeaderAvailableCallback = newBlockHeaderAvailableCallback;
    }

    /**
     * Sets the minimum expected block timestamp (in seconds).
     *  The BlockHeaderDownloader will not go to sleep (unless interrupted) before its most recent blockHeader's
     *  timestamp is at least the minBlockTimestamp.
     */
    public void setMinBlockTimestamp(final Long minBlockTimestampInSeconds) {
        _minBlockTimestamp = minBlockTimestampInSeconds;
    }

    public Container<Float> getAverageBlockHeadersPerSecondContainer() {
        return _averageBlockHeadersPerSecond;
    }

    public Long getBlockHeight() {
        return _blockHeight;
    }

    /**
     * When headers are received, they are processed as a batch.
     *  After each batch completes, the NewBlockHeaderAvailableCallback is invoked.
     *  This setting controls the size of the batch.
     *  The default value is 2000.
     */
    public void setMaxHeaderBatchSize(final Integer batchSize) {
        _maxHeaderBatchSize = batchSize;
    }

    public Integer getMaxHeaderBatchSize() {
        return _maxHeaderBatchSize;
    }
}

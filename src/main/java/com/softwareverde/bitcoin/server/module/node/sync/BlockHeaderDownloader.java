package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.MultiConnectionDatabaseContext;
import com.softwareverde.bitcoin.context.NetworkTimeContext;
import com.softwareverde.bitcoin.context.NodeManagerContext;
import com.softwareverde.bitcoin.context.SystemTimeContext;
import com.softwareverde.bitcoin.context.ThreadPoolContext;
import com.softwareverde.bitcoin.context.UpgradeScheduleContext;
import com.softwareverde.bitcoin.context.core.BlockHeaderValidatorContext;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeFilter;
import com.softwareverde.bitcoin.server.module.node.sync.inventory.BitcoinNodeBlockInventoryTracker;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.concurrent.atomic.AtomicBoolean;

public class BlockHeaderDownloader extends SleepyService {
    public interface Context extends MultiConnectionDatabaseContext, NetworkTimeContext, NodeManagerContext, SystemTimeContext, ThreadPoolContext, DifficultyCalculatorFactory, UpgradeScheduleContext { }

    public interface NewBlockHeadersAvailableCallback {
        void onNewHeadersReceived(BitcoinNode bitcoinNode, List<BlockHeader> blockHeaders);
    }

    public static final Long MAX_TIMEOUT_MS = (15L * 1000L); // 15 Seconds...

    protected final Context _context;

    protected final BitcoinNodeBlockInventoryTracker _blockInventoryTracker;
    protected final BitcoinNode.DownloadBlockHeadersCallback _downloadBlockHeadersCallback;

    protected final Container<Float> _averageBlockHeadersPerSecond = new Container<Float>(0F);
    protected final MilliTimer _timer;

    protected final Object _headersDownloadedPin = new Object();
    protected final AtomicBoolean _isProcessingHeaders = new AtomicBoolean(false);
    protected final Object _genesisBlockPin = new Object();
    protected Boolean _hasGenesisBlock = false;

    protected Long _headBlockHeight = 0L;
    protected Sha256Hash _lastBlockHash = BlockHeader.GENESIS_BLOCK_HASH;
    protected BlockHeader _lastBlockHeader = null;
    protected Long _minBlockTimestamp;
    protected Long _blockHeaderCount = 0L;

    protected NewBlockHeadersAvailableCallback _newBlockHeaderAvailableCallback = null;

    protected Boolean _checkForGenesisBlockHeader() throws DatabaseException {
        final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final Sha256Hash lastKnownHash = blockHeaderDatabaseManager.getHeadBlockHeaderHash();

            synchronized (_genesisBlockPin) {
                _hasGenesisBlock = (lastKnownHash != null);
                _genesisBlockPin.notifyAll();
            }

            return _hasGenesisBlock;
        }
    }

    protected void _downloadGenesisBlock() {
        final Runnable retryGenesisBlockDownload = new Runnable() {
            @Override
            public void run() {
                try { Thread.sleep(5000L); } catch (final InterruptedException exception) { return; }
                _downloadGenesisBlock();
            }
        };

        final ThreadPool threadPool = _context.getThreadPool();

        final BitcoinNodeManager bitcoinNodeManager = _context.getBitcoinNodeManager();
        final List<BitcoinNode> bitcoinNodes = bitcoinNodeManager.getNodes(new NodeFilter() {
            @Override
            public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                final Boolean hasBlockchainEnabled = bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                return Util.coalesce(hasBlockchainEnabled, false);
            }
        });

        if (bitcoinNodes.isEmpty()) {
            threadPool.execute(retryGenesisBlockDownload);
            return;
        }

        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            bitcoinNode.requestBlock(Block.GENESIS_BLOCK_HASH, new BitcoinNode.DownloadBlockCallback() {
                @Override
                public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                    try {
                        if (_checkForGenesisBlockHeader()) { return; } // NOTE: This can happen if the BlockDownloader received the GenesisBlock first...
                    }
                    catch (final Exception exception) {
                        Logger.warn("Unable to check for Genesis Block before processing.", exception);
                    }

                    final Sha256Hash blockHash = block.getHash();
                    Logger.trace("GENESIS RECEIVED: " + blockHash);

                    final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

                    boolean genesisBlockWasStored = false;
                    try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                        genesisBlockWasStored = _validateAndStoreBlockHeader(block, _headBlockHeight, databaseManager);
                    }
                    catch (final DatabaseException databaseException) {
                        Logger.debug(databaseException);
                    }
                    if (! genesisBlockWasStored) {
                        threadPool.execute(retryGenesisBlockDownload);
                        return;
                    }

                    Logger.trace("GENESIS STORED: " + block.getHash());

                    synchronized (_genesisBlockPin) {
                        _hasGenesisBlock = true;
                        _genesisBlockPin.notifyAll();
                    }
                }

                @Override
                public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                    threadPool.execute(retryGenesisBlockDownload);
                }
            });
        }
    }

    protected List<BlockId> _insertBlockHeaders(final List<BlockHeader> blockHeaders, final BlockHeaderDatabaseManager blockHeaderDatabaseManager) {
        try {
            return blockHeaderDatabaseManager.insertBlockHeaders(blockHeaders);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    protected Boolean _validateAndStoreBlockHeader(final BlockHeader blockHeader, final Long blockHeight, final DatabaseManager databaseManager) throws DatabaseException {
        final Sha256Hash blockHash = blockHeader.getHash();

        if (! blockHeader.isValid()) {
            Logger.info("Invalid BlockHeader: " + blockHash);
            return false;
        }

        final VolatileNetworkTime networkTime = _context.getNetworkTime();
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            TransactionUtil.startTransaction(databaseConnection);
            final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);

            if (blockId == null) {
                Logger.info("Error storing BlockHeader: " + blockHash);
                TransactionUtil.rollbackTransaction(databaseConnection);
                return false;
            }

            final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
            final DifficultyCalculatorFactory difficultyCalculatorFactory = _context;
            final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
            final BlockHeaderValidatorContext blockHeaderValidatorContext = new BlockHeaderValidatorContext(blockchainSegmentId, databaseManager, networkTime, difficultyCalculatorFactory, upgradeSchedule);

            final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(blockHeaderValidatorContext);

            final BlockHeaderValidator.BlockHeaderValidationResult blockHeaderValidationResult = blockHeaderValidator.validateBlockHeader(blockHeader, blockHeight);
            if (! blockHeaderValidationResult.isValid) {
                Logger.info("Invalid BlockHeader: " + blockHeaderValidationResult.errorMessage + " (" + blockHash + ")");
                TransactionUtil.rollbackTransaction(databaseConnection);
                return false;
            }

            _headBlockHeight = Math.max(blockHeight, _headBlockHeight);

            TransactionUtil.commitTransaction(databaseConnection);
        }

        return true;
    }

    protected Boolean _validateAndStoreBlockHeaders(final List<BlockHeader> blockHeaders, final DatabaseManager databaseManager, final MutableList<Sha256Hash> nullableInvalidBlockHashes) throws DatabaseException {
        if (blockHeaders.isEmpty()) { return true; }

        final VolatileNetworkTime networkTime = _context.getNetworkTime();
        final DifficultyCalculatorFactory difficultyCalculatorFactory = _context;
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            { // Validate blockHeaders are sequential...
                final BlockHeader firstBlockHeader = blockHeaders.get(0);
                if (! firstBlockHeader.isValid()) {
                    if (nullableInvalidBlockHashes != null) {
                        final Sha256Hash blockHash = firstBlockHeader.getHash();
                        nullableInvalidBlockHashes.add(blockHash);
                    }
                    return false;
                }

                final BlockId previousBlockId = blockHeaderDatabaseManager.getBlockHeaderId(firstBlockHeader.getPreviousBlockHash());
                final boolean previousBlockExists = (previousBlockId != null);
                if (! previousBlockExists) {
                    final Boolean isGenesisBlock = Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, firstBlockHeader.getHash());
                    if (! isGenesisBlock) {
                        // NOTE: Previous block not existing does not qualify the block as indefinitely invalid.
                        return false;
                    }
                }
                else {
                    final Boolean isContentiousBlock = blockHeaderDatabaseManager.hasChildBlock(previousBlockId);
                    if (isContentiousBlock) {
                        // BlockHeaders cannot be batched due to potential forks...
                        long blockHeight = (blockHeaderDatabaseManager.getBlockHeight(previousBlockId) + 1L);
                        for (final BlockHeader blockHeader : blockHeaders) {
                            final Boolean isValid = _validateAndStoreBlockHeader(blockHeader, blockHeight, databaseManager);
                            if (! isValid) {
                                if (nullableInvalidBlockHashes != null) {
                                    final Sha256Hash blockHash = blockHeader.getHash();
                                    nullableInvalidBlockHashes.add(blockHash);
                                }
                                return false;
                            }
                            blockHeight += 1L;
                        }
                        return true;
                    }
                }
                Sha256Hash previousBlockHash = firstBlockHeader.getPreviousBlockHash();
                for (final BlockHeader blockHeader : blockHeaders) {
                    if (! blockHeader.isValid()) {
                        if (nullableInvalidBlockHashes != null) {
                            final Sha256Hash blockHash = blockHeader.getHash();
                            nullableInvalidBlockHashes.add(blockHash);
                        }
                        return false;
                    }
                    if (! Util.areEqual(previousBlockHash, blockHeader.getPreviousBlockHash())) {
                        // NOTE: Non-sequential transmission of blocks does not indefinitely invalidate the block.
                        return false;
                    }
                    previousBlockHash = blockHeader.getHash();
                }
            }

            TransactionUtil.startTransaction(databaseConnection);

            final List<BlockId> blockIds = _insertBlockHeaders(blockHeaders, blockHeaderDatabaseManager);
            if ( (blockIds == null) || (blockIds.isEmpty()) ) {
                TransactionUtil.rollbackTransaction(databaseConnection);

                final BlockHeader firstBlockHeader = blockHeaders.get(0);
                final Sha256Hash blockHash = firstBlockHeader.getHash();
                Logger.info("Invalid BlockHeader: " + blockHash);

                if (nullableInvalidBlockHashes != null) {
                    nullableInvalidBlockHashes.add(blockHash);
                }

                return false;
            }

            final BlockId firstBlockHeaderId = blockIds.get(0);
            final Long firstBlockHeight = blockHeaderDatabaseManager.getBlockHeight(firstBlockHeaderId);

            final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(firstBlockHeaderId);


            final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
            final BlockHeaderValidatorContext blockHeaderValidatorContext = new BlockHeaderValidatorContext(blockchainSegmentId, databaseManager, networkTime, difficultyCalculatorFactory, upgradeSchedule);

            final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(blockHeaderValidatorContext);

            long nextBlockHeight = firstBlockHeight;
            for (final BlockHeader blockHeader : blockHeaders) {
                final BlockHeaderValidator.BlockHeaderValidationResult blockHeaderValidationResult = blockHeaderValidator.validateBlockHeader(blockHeader, nextBlockHeight);
                if (!blockHeaderValidationResult.isValid) {
                    Logger.info("Invalid BlockHeader: " + blockHeaderValidationResult.errorMessage);
                    TransactionUtil.rollbackTransaction(databaseConnection);

                    if (nullableInvalidBlockHashes != null) {
                        final Sha256Hash blockHash = blockHeader.getHash();
                        nullableInvalidBlockHashes.add(blockHash);
                    }

                    return false;
                }

                nextBlockHeight += 1L;
            }

            final long blockHeight = (nextBlockHeight - 1L);
            _headBlockHeight = Math.max(blockHeight, _headBlockHeight);

            TransactionUtil.commitTransaction(databaseConnection);

            return true;
        }
    }

    protected Boolean _processBlockHeaders(final List<BlockHeader> blockHeaders, final BitcoinNode bitcoinNode) {
        final MilliTimer storeHeadersTimer = new MilliTimer();
        storeHeadersTimer.start();

        final int blockHeaderCount = blockHeaders.getCount();
        if (blockHeaderCount == 0) { return true; }

        final BlockHeader firstBlockHeader = blockHeaders.get(0);
        Logger.debug("Downloaded Block headers: "+ firstBlockHeader.getHash() + " + " + blockHeaderCount);

        final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final MutableList<Sha256Hash> invalidBlockHashes = new MutableList<Sha256Hash>(0);
            final Boolean headersAreValid = _validateAndStoreBlockHeaders(blockHeaders, databaseManager, invalidBlockHashes);
            if (! headersAreValid) {
                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                for (final Sha256Hash invalidBlockHash : invalidBlockHashes) {
                    Logger.info("Marking " + invalidBlockHash + " as invalid.");
                    blockHeaderDatabaseManager.markBlockAsInvalid(invalidBlockHash, BlockHeaderDatabaseManager.INVALID_PROCESS_THRESHOLD); // Auto-ban any invalid headers...
                }
                return false;
            }

            for (final BlockHeader blockHeader : blockHeaders) {
                final Sha256Hash blockHash = blockHeader.getHash();
                _blockInventoryTracker.markInventoryAvailable(blockHash, bitcoinNode);
            }

            final BlockHeader lastBlockHeader = blockHeaders.get(blockHeaderCount - 1);
            final Sha256Hash lastBlockHeaderHash = lastBlockHeader.getHash();

            _lastBlockHeader = lastBlockHeader;
            _lastBlockHash = lastBlockHeaderHash;

            _blockHeaderCount += blockHeaderCount;

            _timer.stop();
            final Long millisecondsElapsed = _timer.getMillisecondsElapsed();
            _averageBlockHeadersPerSecond.value = ( (_blockHeaderCount.floatValue() / millisecondsElapsed) * 1000L );
        }
        catch (final DatabaseException exception) {
            Logger.warn("Processing BlockHeaders failed.", exception);
            return false;
        }

        storeHeadersTimer.stop();
        Logger.info("Stored Block Headers: " + firstBlockHeader.getHash() + " - " + _lastBlockHash + " (" + storeHeadersTimer.getMillisecondsElapsed() + "ms)");
        return true;
    }

    public BlockHeaderDownloader(final Context context, final BitcoinNodeBlockInventoryTracker blockInventoryTracker) {
        _context = context;

        _blockInventoryTracker = blockInventoryTracker;
        _timer = new MilliTimer();

        final SystemTime systemTime = _context.getSystemTime();
        _minBlockTimestamp = (systemTime.getCurrentTimeInSeconds() - 3600L); // Default to an hour ago...

        _downloadBlockHeadersCallback = new BitcoinNode.DownloadBlockHeadersCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
                if (! _isProcessingHeaders.compareAndSet(false, true)) { return; }
                if (_shouldAbort()) { return; }

                try {
                    final Boolean headersAreValid = _processBlockHeaders(blockHeaders, bitcoinNode);
                    if (! headersAreValid) { return; }

                    final NewBlockHeadersAvailableCallback newBlockHeaderAvailableCallback = _newBlockHeaderAvailableCallback;
                    if (newBlockHeaderAvailableCallback != null) {
                        final ThreadPool threadPool = _context.getThreadPool();
                        threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                newBlockHeaderAvailableCallback.onNewHeadersReceived(bitcoinNode, blockHeaders);
                            }
                        });
                    }
                }
                finally {
                    _isProcessingHeaders.set(false);
                    synchronized (_isProcessingHeaders) {
                        _isProcessingHeaders.notifyAll();
                    }

                    synchronized (_headersDownloadedPin) {
                        _headersDownloadedPin.notifyAll();
                    }
                }
            }
        };
    }

    @Override
    protected void _onStart() {
        _timer.start();
        _blockHeaderCount = 0L;

        final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            if (headBlockId != null) {
                _lastBlockHash = blockHeaderDatabaseManager.getBlockHash(headBlockId);
                _headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
            }
            else {
                _lastBlockHash = Block.GENESIS_BLOCK_HASH;
                _headBlockHeight = 0L;
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            _lastBlockHash = Util.coalesce(_lastBlockHash, Block.GENESIS_BLOCK_HASH);
        }

        try {
            if (! _checkForGenesisBlockHeader()) {
                _downloadGenesisBlock();
            }
        }
        catch (final Exception exception) {
            Logger.warn("Unable to check for Genesis Block.", exception);
            try { Thread.sleep(1000L); } catch (final Exception ignored) { }
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

        final BitcoinNodeManager bitcoinNodeManager = _context.getBitcoinNodeManager();
        final List<BitcoinNode> bitcoinNodes = bitcoinNodeManager.getPreferredNodes();
        if (bitcoinNodes.isEmpty()) {
            Logger.debug("No peers available.");
            return false;
        }

        final int index = (int) (Math.random() * bitcoinNodes.getCount());
        final BitcoinNode bitcoinNode = bitcoinNodes.get(index);
        bitcoinNode.requestBlockHeadersAfter(_lastBlockHash, _downloadBlockHeadersCallback);

        synchronized (_headersDownloadedPin) {
            final MilliTimer timer = new MilliTimer();
            timer.start();

            final boolean didTimeout;

            for (int i = 0; i < 30; ++i) {
                if (_shouldAbort()) { return false; }
                try { _headersDownloadedPin.wait(MAX_TIMEOUT_MS / 30); }
                catch (final InterruptedException exception) { return false; }
            }

            // If the _headersDownloadedPin timed out because processing the headers took too long, wait for the processing to complete and then consider it a success.
            synchronized (_isProcessingHeaders) {
                if (_isProcessingHeaders.get()) {
                    while (_isProcessingHeaders.get()) {
                        if (_shouldAbort()) { return false; }
                        try { _isProcessingHeaders.wait(250); }
                        catch (final InterruptedException exception) { return false; }
                    }

                    didTimeout = false;
                }
                else {
                    timer.stop();
                    didTimeout = (timer.getMillisecondsElapsed() >= MAX_TIMEOUT_MS);
                }
            }

            if (didTimeout) {
                // The lastBlockHeader may be null when first starting.
                if (_lastBlockHeader == null) { return true; }

                // Don't sleep after a timeout while the most recent block timestamp is less than the minBlockTimestamp...
                return (_lastBlockHeader.getTimestamp() < _minBlockTimestamp);
            }
        }

        return (! _shouldAbort());
    }

    @Override
    protected void _onSleep() { }

    public void setNewBlockHeaderAvailableCallback(final NewBlockHeadersAvailableCallback newBlockHeaderAvailableCallback) {
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
        return _headBlockHeight;
    }

    public void onNewBlockHeaders(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
        final Boolean headersAreValid = _processBlockHeaders(blockHeaders, bitcoinNode);
        if (! headersAreValid) { return; }

        final NewBlockHeadersAvailableCallback newBlockHeaderAvailableCallback = _newBlockHeaderAvailableCallback;
        if (newBlockHeaderAvailableCallback != null) {
            final ThreadPool threadPool = _context.getThreadPool();
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    newBlockHeaderAvailableCallback.onNewHeadersReceived(bitcoinNode, blockHeaders);
                }
            });
        }
    }
}

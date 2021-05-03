package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.context.lazy.LazyBlockValidatorContext;
import com.softwareverde.bitcoin.inflater.BlockHeaderInflaters;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStoreCore;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

public class ChainValidationModule {
    protected final BitcoinProperties _bitcoinProperties;
    protected final Environment _environment;
    protected final Sha256Hash _startingBlockHash;
    protected final PendingBlockStore _blockStore;
    protected final CheckpointConfiguration _checkpointConfiguration;

    public ChainValidationModule(final BitcoinProperties bitcoinProperties, final Environment environment, final String startingBlockHash) {
        _bitcoinProperties = bitcoinProperties;
        _environment = environment;

        final MasterInflater masterInflater = new CoreInflater();
        _startingBlockHash = Util.coalesce(Sha256Hash.fromHexString(startingBlockHash), BlockHeader.GENESIS_BLOCK_HASH);

        { // Initialize the BlockStore...
            final String dataDirectory = bitcoinProperties.getDataDirectory();
            final BlockHeaderInflaters blockHeaderInflaters = masterInflater;
            final BlockInflaters blockInflaters = masterInflater;
            _blockStore = new PendingBlockStoreCore(dataDirectory, blockHeaderInflaters, blockInflaters) {
                @Override
                protected void _deletePendingBlockData(final String blockPath) {
                    if (bitcoinProperties.isDeletePendingBlocksEnabled()) {
                        super._deletePendingBlockData(blockPath);
                    }
                }
            };
        }

        _checkpointConfiguration = new CheckpointConfiguration();
    }

    public void run() {
        final Thread mainThread = Thread.currentThread();
        mainThread.setPriority(Thread.MAX_PRIORITY);

        final Database database = _environment.getDatabase();
        // final MasterDatabaseManagerCache masterDatabaseManagerCache = _environment.getMasterDatabaseManagerCache();

        final MasterInflater masterInflater = new CoreInflater();

        final BlockchainSegmentId blockchainSegmentId;
        final DatabaseConnectionFactory databaseConnectionPool = _environment.getDatabaseConnectionFactory();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionPool, database.getMaxQueryBatchSize(), _blockStore, masterInflater, _checkpointConfiguration);
        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(headBlockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            throw new RuntimeException(exception);
        }

        Sha256Hash nextBlockHash = _startingBlockHash;
        try (final DatabaseConnection databaseConnection = database.newConnection();) {
            final FullNodeDatabaseManager databaseManager = new FullNodeDatabaseManager(
                databaseConnection,
                database.getMaxQueryBatchSize(),
                _blockStore,
                masterInflater,
                _checkpointConfiguration,
                _bitcoinProperties.getMaxCachedUtxoCount(),
                _bitcoinProperties.getUtxoCachePurgePercent()
            );

            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final SystemTime systemTime = new SystemTime();
            final VolatileNetworkTime networkTime = NetworkTime.fromSystemTime(systemTime);

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            final Long maxBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);

            Long validatedTransactionCount = 0L;
            final Long startTime = System.currentTimeMillis();
            while (true) {
                final Sha256Hash blockHash = nextBlockHash;

                final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(nextBlockHash);
                final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

                final int percentComplete = (int) ((blockHeight * 100) / maxBlockHeight.floatValue());

                if (blockHeight % (maxBlockHeight / 100) == 0) {
                    final int secondsElapsed;
                    final float blocksPerSecond;
                    final float transactionsPerSecond;
                    {
                        final Long now = System.currentTimeMillis();
                        final int seconds = (int) ((now - startTime) / 1000L);
                        final long blockCount = blockHeight;
                        blocksPerSecond = (blockCount / (seconds + 1F));
                        secondsElapsed = seconds;
                        transactionsPerSecond = (validatedTransactionCount / (seconds + 1F));
                    }

                    Logger.info(percentComplete + "% complete. " + blockHeight + " of " + maxBlockHeight + " - " + blockHash + " ("+ String.format("%.2f", blocksPerSecond) +" bps) (" + String.format("%.2f", transactionsPerSecond) + " tps) ("+ StringUtil.formatNumberString(secondsElapsed) +" seconds)");
                }

                final MilliTimer blockInflaterTimer = new MilliTimer();
                blockInflaterTimer.start();
                final boolean blockIsCached;
                final Block block;
                {
                    Block cachedBlock = null;
                    if (_blockStore != null) {
                        cachedBlock = _blockStore.getBlock(blockHash, blockHeight);
                    }

                    if (cachedBlock != null) {
                        block = cachedBlock;
                        blockIsCached = true;
                    }
                    else {
                        block = blockDatabaseManager.getBlock(blockId);
                        blockIsCached = false;
                    }
                }
                blockInflaterTimer.stop();
                System.out.println("Block Inflation: " +  block.getHash() + " " + blockInflaterTimer.getMillisecondsElapsed() + "ms");

                validatedTransactionCount += blockDatabaseManager.getTransactionCount(blockId);

                final BlockValidator blockValidator;
                {
                    final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory() {
                        @Override
                        public TransactionValidator getTransactionValidator(final BlockOutputs blockOutputs, final TransactionValidator.Context transactionValidatorContext) {
                            return new TransactionValidatorCore(blockOutputs, transactionValidatorContext);
                        }
                    };

                    final DifficultyCalculatorFactory difficultyCalculatorFactory = new DifficultyCalculatorFactory() {

                        @Override
                        public DifficultyCalculator newDifficultyCalculator(final DifficultyCalculatorContext context) {
                            return new DifficultyCalculator(context);
                        }
                    };

                    final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

                    final TransactionInflaters transactionInflaters = masterInflater;

                    final MutableUnspentTransactionOutputSet unspentTransactionOutputSet = new MutableUnspentTransactionOutputSet();
                    final LazyBlockValidatorContext blockValidatorContext = new LazyBlockValidatorContext(transactionInflaters, blockchainSegmentId, unspentTransactionOutputSet, difficultyCalculatorFactory, transactionValidatorFactory, databaseManager, networkTime, upgradeSchedule);

                    blockValidator = new BlockValidator(blockValidatorContext);
                    blockValidator.setMaxThreadCount(_bitcoinProperties.getMaxThreadCount());
                    blockValidator.setShouldLogValidBlocks(true);
                    blockValidator.setTrustedBlockHeight(BlockValidator.DO_NOT_TRUST_BLOCKS);
                }

                final BlockValidationResult blockValidationResult = blockValidator.validateBlock(block, blockHeight);

                if (! blockValidationResult.isValid) {
                    Logger.error("Invalid block found: " + blockHash + "(" + blockValidationResult.errorMessage + ")");
                    break;
                }

                if ( (! blockIsCached) && (_blockStore != null) ) {
                    _blockStore.storeBlock(block, blockHeight);
                }

                nextBlockHash = null;
                final BlockId nextBlockId = blockHeaderDatabaseManager.getChildBlockId(headBlockchainSegmentId, blockId);
                if (nextBlockId != null) {
                    final Boolean nextBlockHasTransactions = blockDatabaseManager.hasTransactions(nextBlockId);
                    if (nextBlockHasTransactions) {
                        nextBlockHash = blockHeaderDatabaseManager.getBlockHash(nextBlockId);
                    }
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.error("Last validated block: " + nextBlockHash, exception);
            BitcoinUtil.exitFailure();
        }

        System.exit(0);
    }
}

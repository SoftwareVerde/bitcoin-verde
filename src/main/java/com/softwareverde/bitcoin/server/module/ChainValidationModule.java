package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.*;
import com.softwareverde.bitcoin.block.*;
import com.softwareverde.bitcoin.block.header.*;
import com.softwareverde.bitcoin.block.validator.*;
import com.softwareverde.bitcoin.chain.segment.*;
import com.softwareverde.bitcoin.context.*;
import com.softwareverde.bitcoin.context.lazy.*;
import com.softwareverde.bitcoin.inflater.*;
import com.softwareverde.bitcoin.server.*;
import com.softwareverde.bitcoin.server.configuration.*;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.pool.*;
import com.softwareverde.bitcoin.server.module.node.database.*;
import com.softwareverde.bitcoin.server.module.node.database.block.*;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.*;
import com.softwareverde.bitcoin.server.module.node.database.block.header.*;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.*;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.*;
import com.softwareverde.bitcoin.server.module.node.store.*;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.bitcoin.util.*;
import com.softwareverde.database.*;
import com.softwareverde.logging.*;
import com.softwareverde.network.time.*;
import com.softwareverde.security.hash.sha256.*;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.*;
import com.softwareverde.util.type.time.*;

public class ChainValidationModule {
    protected final BitcoinProperties _bitcoinProperties;
    protected final Environment _environment;
    protected final Sha256Hash _startingBlockHash;
    protected final PendingBlockStore _blockStore;

    public ChainValidationModule(final BitcoinProperties bitcoinProperties, final Environment environment, final String startingBlockHash) {
        _bitcoinProperties = bitcoinProperties;
        _environment = environment;

        final MasterInflater masterInflater = new CoreInflater();
        _startingBlockHash = Util.coalesce(Sha256Hash.fromHexString(startingBlockHash), BlockHeader.GENESIS_BLOCK_HASH);

        { // Initialize the BlockCache...
            final String blockCacheDirectory = (bitcoinProperties.getDataDirectory() + "/" + BitcoinProperties.DATA_CACHE_DIRECTORY_NAME + "/blocks");
            final String pendingBlockCacheDirectory = (bitcoinProperties.getDataDirectory() + "/" + BitcoinProperties.DATA_CACHE_DIRECTORY_NAME + "/pending-blocks");
            _blockStore = new PendingBlockStoreCore(blockCacheDirectory, pendingBlockCacheDirectory, masterInflater);
        }
    }

    public void run() {
        final Thread mainThread = Thread.currentThread();
        mainThread.setPriority(Thread.MAX_PRIORITY);

        final Database database = _environment.getDatabase();
        // final MasterDatabaseManagerCache masterDatabaseManagerCache = _environment.getMasterDatabaseManagerCache();

        final MasterInflater masterInflater = new CoreInflater();

        final BlockchainSegmentId blockchainSegmentId;
        final DatabaseConnectionPool databaseConnectionPool = _environment.getDatabaseConnectionPool();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionPool, _blockStore, masterInflater);
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
                _blockStore,
                masterInflater,
                _bitcoinProperties.getMaxCachedUtxoCount(),
                _bitcoinProperties.getUtxoCachePurgePercent()
            );

            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final SystemTime systemTime = new SystemTime();
            final NetworkTime networkTime = ImmutableNetworkTime.fromSeconds(systemTime.getCurrentTimeInSeconds());

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

                final BlockValidator<?> blockValidator;
                {
                    final LazyMutableUnspentTransactionOutputSet unspentTransactionOutputSet = new LazyMutableUnspentTransactionOutputSet(databaseManagerFactory);
                    final BlockValidatorContext blockValidatorContext = new BlockValidatorContext(blockchainSegmentId, unspentTransactionOutputSet, databaseManager, networkTime);
                    blockValidator = new BlockValidator<>(blockValidatorContext);
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

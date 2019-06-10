package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.block.core.CoreBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManagerFactory;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Util;

public class ChainValidationModule {
    protected final BitcoinProperties _bitcoinProperties;
    protected final Environment _environment;
    protected final Sha256Hash _startingBlockHash;

    public ChainValidationModule(final BitcoinProperties bitcoinProperties, final Environment environment, final String startingBlockHash) {
        _bitcoinProperties = bitcoinProperties;
        _environment = environment;

        _startingBlockHash = Util.coalesce(Sha256Hash.fromHexString(startingBlockHash), BlockHeader.GENESIS_BLOCK_HASH);
    }

    public void run() {
        final Database database = _environment.getDatabase();
        final MasterDatabaseManagerCache masterDatabaseManagerCache = _environment.getMasterDatabaseManagerCache();

        Sha256Hash nextBlockHash = _startingBlockHash;
        try (
            final DatabaseConnection databaseConnection = database.newConnection();
            final DatabaseManagerCache databaseManagerCache = new LocalDatabaseManagerCache(masterDatabaseManagerCache)
        ) {

            final CoreDatabaseManager databaseManager = new CoreDatabaseManager(databaseConnection, databaseManagerCache);

            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final CoreBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final NetworkTime networkTime = new MutableNetworkTime();
            final MutableMedianBlockTime medianBlockTime = blockHeaderDatabaseManager.initializeMedianBlockTime();

            final DatabaseConnectionFactory databaseConnectionFactory = database.newConnectionFactory();
            final ReadUncommittedDatabaseConnectionFactory readUncommittedDatabaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactory(databaseConnectionFactory);
            final CoreDatabaseManagerFactory databaseManagerFactory = new CoreDatabaseManagerFactory(readUncommittedDatabaseConnectionFactory, databaseManagerCache);

            final BlockValidator blockValidator = new BlockValidator(databaseManagerFactory, networkTime, medianBlockTime);
            blockValidator.setMaxThreadCount(_bitcoinProperties.getMaxThreadCount());
            blockValidator.setShouldLogValidBlocks(false);
            blockValidator.setTrustedBlockHeight(BlockValidator.DO_NOT_TRUST_BLOCKS);

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
                    final Integer secondsElapsed;
                    final Float blocksPerSecond;
                    final Float transactionsPerSecond;
                    {
                        final Long now = System.currentTimeMillis();
                        final Integer seconds = (int) ((now - startTime) / 1000L);
                        final Long blockCount = blockHeight;
                        blocksPerSecond = (blockCount / (seconds.floatValue() + 1));
                        secondsElapsed = seconds;
                        transactionsPerSecond = (validatedTransactionCount / (seconds.floatValue() + 1));
                    }

                    Logger.log(percentComplete + "% complete. " + blockHeight + " of " + maxBlockHeight + " - " + blockHash + " ("+ String.format("%.2f", blocksPerSecond) +" bps) (" + String.format("%.2f", transactionsPerSecond) + " tps) ("+ StringUtil.formatNumberString(secondsElapsed) +" seconds)");
                }

                final Block block = blockDatabaseManager.getBlock(blockId, true);

                validatedTransactionCount += blockDatabaseManager.getTransactionCount(blockId);
                final BlockValidationResult blockValidationResult = blockValidator.validateBlock(blockId, block);

                if (! blockValidationResult.isValid) {
                    Logger.error("Invalid block found: " + blockHash + "(" + blockValidationResult.errorMessage + ")");
                    break;
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
            Logger.log(exception);
            Logger.log("Last validated block: " + nextBlockHash);
            BitcoinUtil.exitFailure();
        }

        _environment.getMasterDatabaseManagerCache().close();

        System.exit(0);
    }
}

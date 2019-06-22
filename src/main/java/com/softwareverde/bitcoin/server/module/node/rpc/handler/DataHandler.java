package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionDownloader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.connection.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;

import java.io.File;

public class DataHandler implements NodeRpcHandler.DataHandler {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final TransactionValidatorFactory _transactionValidatorFactory;

    protected final NetworkTime _networkTime;
    protected final MedianBlockTimeWithBlocks _medianBlockTime;

    protected final TransactionDownloader _transactionDownloader;
    protected final BlockDownloader _blockDownloader;

    protected String _cachedBlockDirectory = null;
    protected final Integer _blocksPerCacheDirectory = 2016; // About 2 weeks...

    protected String _getCachedBlockDirectory(final Long blockHeight) {
        final String cachedBlockDirectory = _cachedBlockDirectory;
        if (cachedBlockDirectory == null) { return null; }

        final Long blockHeightDirectory = (blockHeight / _blocksPerCacheDirectory);
        return (cachedBlockDirectory + "/" + blockHeightDirectory);
    }

    protected String _getCachedBlockPath(final Sha256Hash blockHash, final Long blockHeight) {
        final String cachedBlockDirectory = _cachedBlockDirectory;
        if (cachedBlockDirectory == null) { return null; }

        final String blockHeightDirectory = _getCachedBlockDirectory(blockHeight);
        return (blockHeightDirectory + "/" + blockHash);
    }

    protected void _cacheBlock(final Block block, final Long blockHeight) {
        if (_cachedBlockDirectory == null) { return; }

        final Sha256Hash blockHash = block.getHash();

        final String blockPath = _getCachedBlockPath(blockHash, blockHeight);
        if (blockPath == null) { return; }

        if (IoUtil.fileExists(blockPath)) { return; }

        { // Create the directory, if necessary...
            final String cacheDirectory = _getCachedBlockDirectory(blockHeight);
            final File directory = new File(cacheDirectory);
            if (! directory.exists()) {
                final Boolean mkdirSuccessful = directory.mkdirs();
                if (! mkdirSuccessful) {
                    Logger.log("Unable to create block cache directory: " + cacheDirectory);
                    return;
                }
            }
        }

        final BlockDeflater blockDeflater = new BlockDeflater();
        final MutableByteArray byteArray = blockDeflater.toBytes(block);

        IoUtil.putFileContents(blockPath, byteArray.unwrap());
    }

    protected Block _getCachedBlock(final Sha256Hash blockHash, final Long blockHeight) {
        if (_cachedBlockDirectory == null) { return null; }

        final String blockPath = _getCachedBlockPath(blockHash, blockHeight);
        if (blockPath == null) { return null; }

        if (! IoUtil.fileExists(blockPath)) { return null; }
        final ByteArray blockBytes = MutableByteArray.wrap(IoUtil.getFileContents(blockPath));
        if (blockBytes == null) { return null; }

        final BlockInflater blockInflater = new BlockInflater();
        return blockInflater.fromBytes(blockBytes);
    }

    public DataHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory, final TransactionValidatorFactory transactionValidatorFactory, final TransactionDownloader transactionDownloader, final BlockDownloader blockDownloader, final NetworkTime networkTime, final MedianBlockTimeWithBlocks medianBlockTime) {
        _transactionValidatorFactory = transactionValidatorFactory;
        _databaseManagerFactory = databaseManagerFactory;
        _transactionDownloader = transactionDownloader;
        _blockDownloader = blockDownloader;

        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public void setCachedBlockDirectory(final String cachedBlockDirectory) {
        _cachedBlockDirectory = cachedBlockDirectory;
    }

    public String getCachedBlockDirectory() {
        return _cachedBlockDirectory;
    }

    @Override
    public Long getBlockHeaderHeight() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId blockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            if (blockId == null) { return 0L; }

            return blockHeaderDatabaseManager.getBlockHeight(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Long getBlockHeight() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId blockId = blockDatabaseManager.getHeadBlockId();
            if (blockId == null) { return 0L; }

            return blockHeaderDatabaseManager.getBlockHeight(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Long getBlockHeaderTimestamp() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            if (headBlockId == null) { return MedianBlockTime.GENESIS_BLOCK_TIMESTAMP; }

            return blockHeaderDatabaseManager.getBlockTimestamp(headBlockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Long getBlockTimestamp() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            if (headBlockId == null) { return MedianBlockTime.GENESIS_BLOCK_TIMESTAMP; }

            return blockHeaderDatabaseManager.getBlockTimestamp(headBlockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public List<BlockHeader> getBlockHeaders(final Long nullableBlockHeight, final Integer maxBlockCount) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final Long startingBlockHeight;
            {
                if (nullableBlockHeight != null) {
                    startingBlockHeight = nullableBlockHeight;
                }
                else {
                    final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                    startingBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
                }
            }

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final ImmutableListBuilder<BlockHeader> blockHeaders = new ImmutableListBuilder<BlockHeader>(maxBlockCount);
            for (int i = 0; i < maxBlockCount; ++i) {
                if (startingBlockHeight < i) { break; }

                final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, (startingBlockHeight - i));
                if (blockId == null) { break; }

                final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                if (blockHeader == null) { continue; }

                blockHeaders.add(blockHeader);
            }
            return blockHeaders.build();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public BlockHeader getBlockHeader(final Long blockHeight) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);
            if (blockId == null) { return null; }

            return blockHeaderDatabaseManager.getBlockHeader(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public BlockHeader getBlockHeader(final Sha256Hash blockHash) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            if (blockId == null) { return null; }

            return blockHeaderDatabaseManager.getBlockHeader(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Block getBlock(final Long blockHeight) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);
            if (blockId == null) { return null; }

            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
            final Block cachedBlock = _getCachedBlock(blockHash, blockHeight);
            if (cachedBlock != null) {
                return cachedBlock;
            }

            final Block block = blockDatabaseManager.getBlock(blockId);
            _cacheBlock(block, blockHeight);
            return block;
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Block getBlock(final Sha256Hash blockHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            if (blockId == null) { return null; }

            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            final Block cachedBlock = _getCachedBlock(blockHash, blockHeight);
            if (cachedBlock != null) {
                return cachedBlock;
            }

            final Block block = blockDatabaseManager.getBlock(blockId);
            _cacheBlock(block, blockHeight);
            return block;
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Transaction getTransaction(final Sha256Hash transactionHash) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId == null) { return null; }

            return transactionDatabaseManager.getTransaction(transactionId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Difficulty getDifficulty() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseManager);
            return difficultyCalculator.calculateRequiredDifficulty();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getUnconfirmedTransactions() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();

            final ImmutableListBuilder<Transaction> unconfirmedTransactionsListBuilder = new ImmutableListBuilder<Transaction>(unconfirmedTransactionIds.getSize());
            for (final TransactionId transactionId : unconfirmedTransactionIds) {
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                unconfirmedTransactionsListBuilder.add(transaction);
            }

            return unconfirmedTransactionsListBuilder.build();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public List<TransactionWithFee> getUnconfirmedTransactionsWithFees() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();

            final ImmutableListBuilder<TransactionWithFee> listBuilder = new ImmutableListBuilder<TransactionWithFee>(unconfirmedTransactionIds.getSize());
            for (final TransactionId transactionId : unconfirmedTransactionIds) {
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                if (transaction == null) {
                    Logger.log("NOTICE: Unable to load Unconfirmed Transaction: " + transactionId);
                    continue;
                }
                final Long transactionFee = transactionDatabaseManager.calculateTransactionFee(transaction);

                final TransactionWithFee transactionWithFee = new TransactionWithFee(transaction, transactionFee);
                listBuilder.add(transactionWithFee);
            }

            return listBuilder.build();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Long getBlockReward() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId blockId = blockDatabaseManager.getHeadBlockId();
            if (blockId == null) { return 0L; }

            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

            return BlockHeader.calculateBlockReward(blockHeight);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public BlockValidationResult validatePrototypeBlock(final Block block) {
        Logger.log("Validating Prototype Block: " + block.getHash());

        final DatabaseConnectionFactory databaseConnectionFactory = _databaseManagerFactory.getDatabaseConnectionFactory();
        final ReadUncommittedDatabaseConnectionFactory readUncommittedDatabaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactory(databaseConnectionFactory);
        final DatabaseManagerCache databaseManagerCache = _databaseManagerFactory.getDatabaseManagerCache();

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            try {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    TransactionUtil.startTransaction(databaseConnection);

                    final BlockId blockId = blockDatabaseManager.storeBlock(block);
                    final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(readUncommittedDatabaseConnectionFactory, databaseManagerCache);
                    final BlockValidator blockValidator = new BlockValidator(databaseManagerFactory, _transactionValidatorFactory, _networkTime, _medianBlockTime);
                    return blockValidator.validatePrototypeBlock(blockId, block);
                }
            }
            finally {
                TransactionUtil.rollbackTransaction(databaseConnection); // Never keep the validated block...
            }
        }
        catch (final Exception exception) {
            Logger.log(exception);
            return BlockValidationResult.invalid("An internal error occurred.");
        }
    }

    @Override
    public void submitTransaction(final Transaction transaction) {
        _transactionDownloader.submitTransaction(transaction);
    }

    @Override
    public void submitBlock(final Block block) {
        _blockDownloader.submitBlock(block);
    }
}

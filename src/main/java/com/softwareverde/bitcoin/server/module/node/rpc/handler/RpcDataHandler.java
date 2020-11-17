package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.ValidationResult;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.context.core.TransactionValidatorContext;
import com.softwareverde.bitcoin.context.lazy.CachingMedianBlockTimeContext;
import com.softwareverde.bitcoin.context.lazy.LazyBlockValidatorContext;
import com.softwareverde.bitcoin.context.lazy.LazyDifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.lazy.LazyMutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.context.lazy.LazyUnconfirmedTransactionUtxoSet;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.SlpTransactionProcessor;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionDownloader;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidationCache;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidator;
import com.softwareverde.bitcoin.slp.validator.TransactionAccumulator;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidationResult;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.VolatileNetworkTime;

public class RpcDataHandler implements NodeRpcHandler.DataHandler {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final TransactionInflaters _transactionInflaters;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final DifficultyCalculatorFactory _difficultyCalculatorFactory;
    protected final TransactionValidatorFactory _transactionValidatorFactory;
    protected final VolatileNetworkTime _networkTime;
    protected final TransactionDownloader _transactionDownloader;
    protected final BlockDownloader _blockDownloader;
    protected final BlockchainBuilder _blockchainBuilder;

    protected Block _getBlock(final BlockId blockId, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        if (blockId == null) { return null; }

        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        return blockDatabaseManager.getBlock(blockId);
    }

    protected List<Transaction> _getBlockTransactions(final BlockId blockId, final Integer pageSize, final Integer pageNumber, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        if (blockId == null) { return null; }

        final Block block = _getBlock(blockId, databaseManager);
        if (block == null) { return null; }

        final List<Transaction> transactions = block.getTransactions();

        final MutableList<Transaction> returnedTransactions = new MutableList<Transaction>(pageSize);
        final int startIndex = (pageNumber * pageSize);
        for (int i = 0; i < pageSize; ++i) {
            final int readIndex = (startIndex + i);
            if (readIndex >= transactions.getCount()) { break; }

            final Transaction transaction = transactions.get(readIndex);
            returnedTransactions.add(transaction);
        }
        return returnedTransactions;
    }


    public RpcDataHandler(final TransactionInflaters transactionInflaters, final FullNodeDatabaseManagerFactory databaseManagerFactory, final DifficultyCalculatorFactory difficultyCalculatorFactory, final TransactionValidatorFactory transactionValidatorFactory, final TransactionDownloader transactionDownloader, final BlockchainBuilder blockchainBuilder, final BlockDownloader blockDownloader, final VolatileNetworkTime networkTime, final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
        _transactionInflaters = transactionInflaters;
        _databaseManagerFactory = databaseManagerFactory;
        _difficultyCalculatorFactory = difficultyCalculatorFactory;
        _transactionValidatorFactory = transactionValidatorFactory;

        _transactionDownloader = transactionDownloader;
        _blockDownloader = blockDownloader;
        _blockchainBuilder = blockchainBuilder;
        _networkTime = networkTime;
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
            Logger.debug(exception);
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
            Logger.debug(exception);
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
            Logger.debug(exception);
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
            Logger.debug(exception);
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
            Logger.debug(exception);
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
            Logger.debug(exception);
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
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Long getBlockHeaderHeight(final Sha256Hash blockHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            return blockHeaderDatabaseManager.getBlockHeight(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Block getBlock(final Long blockHeight) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);

            return _getBlock(blockId, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Block getBlock(final Sha256Hash blockHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

            return _getBlock(blockId, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getBlockTransactions(final Sha256Hash blockHash, final Integer pageSize, final Integer pageNumber) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            if (blockId == null) { return null; }

            return _getBlockTransactions(blockId, pageSize, pageNumber, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getBlockTransactions(final Long blockHeight, final Integer pageSize, final Integer pageNumber) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);

            return _getBlockTransactions(blockId, pageSize, pageNumber, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
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
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Difficulty getDifficulty() {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            final Long nextBlockHeight = (blockHeaderDatabaseManager.getBlockHeight(headBlockId) + 1L);

            final LazyDifficultyCalculatorContext difficultyCalculatorContext = new LazyDifficultyCalculatorContext(blockchainSegmentId, databaseManager, _difficultyCalculatorFactory, _upgradeSchedule);
            final DifficultyCalculator difficultyCalculator = difficultyCalculatorContext.newDifficultyCalculator();

            return difficultyCalculator.calculateRequiredDifficulty(nextBlockHeight);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getUnconfirmedTransactions() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();

            final ImmutableListBuilder<Transaction> unconfirmedTransactionsListBuilder = new ImmutableListBuilder<Transaction>(unconfirmedTransactionIds.getCount());
            for (final TransactionId transactionId : unconfirmedTransactionIds) {
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                unconfirmedTransactionsListBuilder.add(transaction);
            }

            return unconfirmedTransactionsListBuilder.build();
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public List<TransactionWithFee> getUnconfirmedTransactionsWithFees() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();

            final ImmutableListBuilder<TransactionWithFee> listBuilder = new ImmutableListBuilder<TransactionWithFee>(unconfirmedTransactionIds.getCount());
            for (final TransactionId transactionId : unconfirmedTransactionIds) {
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                if (transaction == null) {
                    Logger.debug("Unable to load Unconfirmed Transaction: " + transactionId);
                    continue;
                }
                final Long transactionFee = transactionDatabaseManager.calculateTransactionFee(transaction);

                final TransactionWithFee transactionWithFee = new TransactionWithFee(transaction, transactionFee);
                listBuilder.add(transactionWithFee);
            }

            return listBuilder.build();
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
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
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Boolean isSlpTransaction(final Sha256Hash transactionHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final SlpTokenId slpTokenId = transactionDatabaseManager.getSlpTokenId(transactionHash);
            return (slpTokenId != null);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Boolean isValidSlpTransaction(final Sha256Hash transactionHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();

            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId == null) { return false; }

            return slpTransactionDatabaseManager.getSlpTransactionValidationResult(transactionId);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public SlpTokenId getSlpTokenId(final Sha256Hash transactionHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getSlpTokenId(transactionHash);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public BlockValidationResult validatePrototypeBlock(final Block block) {
        Logger.info("Validating Prototype Block: " + block.getHash());

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            try {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    TransactionUtil.startTransaction(databaseConnection);

                    final BlockId blockId = blockDatabaseManager.storeBlock(block);

                    final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                    final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

                    final LazyMutableUnspentTransactionOutputSet unspentTransactionOutputSet = new LazyMutableUnspentTransactionOutputSet();
                    unspentTransactionOutputSet.loadOutputsForBlock(databaseManager, block, blockHeight);

                    final LazyBlockValidatorContext blockValidatorContext = new LazyBlockValidatorContext(_transactionInflaters, blockchainSegmentId, unspentTransactionOutputSet, _difficultyCalculatorFactory, _transactionValidatorFactory, databaseManager, _networkTime, _upgradeSchedule);
                    final BlockValidator blockValidator = new BlockValidator(blockValidatorContext);
                    return blockValidator.validatePrototypeBlock(block, blockHeight);
                }
            }
            finally {
                TransactionUtil.rollbackTransaction(databaseConnection); // Never keep the validated block...
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return BlockValidationResult.invalid("An internal error occurred.");
        }
    }

    @Override
    public ValidationResult validateTransaction(final Transaction transaction, final Boolean enableSlpValidation) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final CachingMedianBlockTimeContext medianBlockTimeContext = new CachingMedianBlockTimeContext(blockchainSegmentId, databaseManager);
            final LazyUnconfirmedTransactionUtxoSet unconfirmedTransactionUtxoSet = new LazyUnconfirmedTransactionUtxoSet(databaseManager);
            final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(_transactionInflaters, _networkTime, medianBlockTimeContext, unconfirmedTransactionUtxoSet, _upgradeSchedule);
            final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

            try {
                TransactionUtil.startTransaction(databaseConnection);
                final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);

                transactionDatabaseManager.storeUnconfirmedTransaction(transaction);
                final TransactionValidationResult transactionValidationResult =  transactionValidator.validateTransaction((headBlockHeight + 1L), transaction);

                if (transactionValidationResult.isValid && enableSlpValidation) {
                    final TransactionAccumulator transactionAccumulator = SlpTransactionProcessor.createTransactionAccumulator(databaseManager, null);
                    final SlpTransactionValidationCache slpTransactionValidationCache = SlpTransactionProcessor.createSlpTransactionValidationCache(databaseManager);
                    final SlpTransactionValidator slpTransactionValidator = new SlpTransactionValidator(transactionAccumulator, slpTransactionValidationCache);

                    final Boolean isValidSlpTransaction = slpTransactionValidator.validateTransaction(transaction);
                    if (! isValidSlpTransaction) {
                        return ValidationResult.invalid("Invalid SLP Transaction.");
                    }
                }

                return transactionValidationResult;
            }
            finally {
                TransactionUtil.rollbackTransaction(databaseConnection); // Never keep the validated transaction...
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return ValidationResult.invalid("An internal error occurred.");
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

    @Override
    public void reconsiderBlock(final Sha256Hash blockHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            blockHeaderDatabaseManager.clearBlockAsInvalid(blockHash);
            _blockchainBuilder.wakeUp();
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }
}

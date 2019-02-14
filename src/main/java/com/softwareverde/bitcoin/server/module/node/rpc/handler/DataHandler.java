package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.*;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.rpc.JsonRpcSocketServerHandler;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionDownloader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class DataHandler implements JsonRpcSocketServerHandler.DataHandler {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected final TransactionDownloader _transactionDownloader;
    protected final BlockDownloader _blockDownloader;

    protected Long _calculateTransactionFee(final Transaction transaction, final TransactionOutputDatabaseManager transactionOutputDatabaseManager) throws DatabaseException {
        Long totalTransactionInputAmount = 0L;
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputId previousTransactionOutputId;
            {
                final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                if (previousOutputTransactionHash != null) {
                    final TransactionOutputIdentifier previousTransactionOutputIdentifier = new TransactionOutputIdentifier(previousOutputTransactionHash, transactionInput.getPreviousOutputIndex());
                    previousTransactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(previousTransactionOutputIdentifier);
                }
                else {
                    previousTransactionOutputId = null;
                }
            }

            if (previousTransactionOutputId == null) { return null; }

            final TransactionOutput previousTransactionOutput = transactionOutputDatabaseManager.getTransactionOutput(previousTransactionOutputId);
            final Long previousTransactionOutputAmount = ( previousTransactionOutput != null ? previousTransactionOutput.getAmount() : null );

            if (previousTransactionOutputAmount == null) { return null; }

            totalTransactionInputAmount += previousTransactionOutputAmount;
        }

        Long totalTransactionOutputAmount = 0L;
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            totalTransactionOutputAmount += transactionOutput.getAmount();
        }

        return (totalTransactionInputAmount - totalTransactionOutputAmount);
    }

    public DataHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final TransactionDownloader transactionDownloader, final BlockDownloader blockDownloader) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
        _transactionDownloader = transactionDownloader;
        _blockDownloader = blockDownloader;
    }

    @Override
    public Long getBlockHeaderHeight() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

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
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

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
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
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
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            if (headBlockId == null) { return MedianBlockTime.GENESIS_BLOCK_TIMESTAMP; }

            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            return blockHeaderDatabaseManager.getBlockTimestamp(headBlockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public List<BlockHeader> getBlockHeaders(final Long nullableBlockHeight, final Integer maxBlockCount) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

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
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
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
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

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
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);
            if (blockId == null) { return null; }

            return blockDatabaseManager.getBlock(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Block getBlock(final Sha256Hash blockHash) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            if (blockId == null) { return null; }

            return blockDatabaseManager.getBlock(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public Transaction getTransaction(final Sha256Hash transactionHash) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);

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
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseConnection, _databaseManagerCache);
            return difficultyCalculator.calculateRequiredDifficulty();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getUnconfirmedTransactions() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
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
    public List<JsonRpcSocketServerHandler.TransactionWithFee> getUnconfirmedTransactionsWithFees() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection, _databaseManagerCache);

            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();

            final ImmutableListBuilder<JsonRpcSocketServerHandler.TransactionWithFee> listBuilder = new ImmutableListBuilder<JsonRpcSocketServerHandler.TransactionWithFee>(unconfirmedTransactionIds.getSize());
            for (final TransactionId transactionId : unconfirmedTransactionIds) {
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                if (transaction == null) {
                    Logger.log("NOTICE: Unable to load Unconfirmed Transaction: " + transactionId);
                    continue;
                }
                final Long transactionFee = _calculateTransactionFee(transaction, transactionOutputDatabaseManager);

                final JsonRpcSocketServerHandler.TransactionWithFee transactionWithFee = new JsonRpcSocketServerHandler.TransactionWithFee(transaction, transactionFee);
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
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

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
    public void submitTransaction(final Transaction transaction) {
        _transactionDownloader.submitTransaction(transaction);
    }

    @Override
    public void submitBlock(final Block block) {
        _blockDownloader.submitBlock(block);
    }
}

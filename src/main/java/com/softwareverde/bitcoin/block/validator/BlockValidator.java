package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.Bip34;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.thread.*;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.Map;

public class BlockValidator {
    protected final NetworkTime _networkTime;
    protected final MedianBlockTimeWithBlocks _medianBlockTime;
    protected final SystemTime _systemTime = new SystemTime();
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected Boolean _shouldLogValidBlocks = true;
    protected Integer _maxThreadCount = 4;
    protected Integer _trustedBlockHeight = 0;

    protected Boolean _validateBlock(final BlockChainSegmentId blockChainSegmentId, final Block block, final Long blockHeight) {
        if (! block.isValid()) {
            Logger.log("Block header is invalid.");
            return false;
        }

        final NanoTimer validateBlockTimer = new NanoTimer();
        validateBlockTimer.start();

        final List<Transaction> transactions;
        final Map<Sha256Hash, Transaction> queuedTransactionOutputs = new HashMap<Sha256Hash, Transaction>();
        { // Remove the coinbase transaction and create a lookup map for transaction outputs...
            final List<Transaction> fullTransactionList = block.getTransactions();
            final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(fullTransactionList.getSize());
            int transactionIndex = 0;
            for (final Transaction transaction : fullTransactionList) {
                if (transactionIndex > 0) {
                    listBuilder.add(transaction);
                }

                queuedTransactionOutputs.put(transaction.getHash(), transaction);

                transactionIndex += 1;
            }
            transactions = listBuilder.build();
        }

        final TaskHandlerFactory<Transaction, Long> totalExpenditureTaskHandlerFactory = new TaskHandlerFactory<Transaction, Long>() {
            @Override
            public TaskHandler<Transaction, Long> newInstance() {
                return new TotalExpenditureTaskHandler(queuedTransactionOutputs);
            }
        };

        final TaskHandlerFactory<Transaction, Boolean> transactionValidationTaskHandlerFactory = new TaskHandlerFactory<Transaction, Boolean>() {
            @Override
            public TaskHandler<Transaction, Boolean> newInstance() {
                return new TransactionValidationTaskHandler(blockChainSegmentId, blockHeight, _networkTime, _medianBlockTime);
            }
        };

        final Integer threadCount = Math.max((_maxThreadCount / 2), 1);

        final ParallelledTaskSpawner<Transaction, Long> totalExpenditureValidationTaskSpawner = new ParallelledTaskSpawner<Transaction, Long>(_databaseConnectionFactory, _databaseManagerCache);
        totalExpenditureValidationTaskSpawner.setTaskHandlerFactory(totalExpenditureTaskHandlerFactory);
        totalExpenditureValidationTaskSpawner.executeTasks(transactions, threadCount);

        if (threadCount == 1) {
            totalExpenditureValidationTaskSpawner.waitForResults(); // Wait for the results synchronously when the threadCount is one...
        }

        final ParallelledTaskSpawner<Transaction, Boolean> transactionValidationTaskSpawner = new ParallelledTaskSpawner<Transaction, Boolean>(_databaseConnectionFactory, _databaseManagerCache);
        transactionValidationTaskSpawner.setTaskHandlerFactory(transactionValidationTaskHandlerFactory);

        final Boolean shouldValidateInputs = (blockHeight > _trustedBlockHeight);
        if (shouldValidateInputs) {
            transactionValidationTaskSpawner.executeTasks(transactions, threadCount);
        }
        else {
            Logger.log("NOTE: Trusting Block Height: " + blockHeight);
            final List<Transaction> emptyTransactionList = new MutableList<Transaction>();
            transactionValidationTaskSpawner.executeTasks(emptyTransactionList, threadCount);
        }

        if (threadCount == 1) {
            transactionValidationTaskSpawner.waitForResults(); // Wait for the results synchronously when the threadCount is one...
        }

        // TODO: Validate block size...
        // TODO: Validate max operations per block... (https://bitcoin.stackexchange.com/questions/35691/if-block-sizes-go-up-wont-sigop-limits-have-to-change-too)
        // TODO: Enforce NULLFAIL (https://github.com/bitcoin/bips/blob/master/bip-0146.mediawiki#nullfail)
        // TODO: Validate transaction does not appear twice within the same Block and BlockChain... (https://github.com/bitcoin/bips/blob/master/bip-0030.mediawiki) (https://github.com/bitcoin/bitcoin/commit/ab91bf39b7c11e9c86bb2043c24f0f377f1cf514)
        // TODO: Create test for PreviousTransactionOutput being EmptyHash/-1 when not coinbase.

        { // Validate coinbase contains block height...
            if (Bip34.isEnabled(blockHeight)) {
                final Long blockVersion = block.getVersion();
                if (blockVersion < 2L) {
                    Logger.log("Invalid block version.");
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return false;
                }

                final CoinbaseTransaction coinbaseTransaction = block.getCoinbaseTransaction();
                final UnlockingScript unlockingScript = coinbaseTransaction.getCoinbaseScript();

                final List<Operation> operations = unlockingScript.getOperations();
                final Operation operation = operations.get(0);
                if (operation.getType() != Operation.Type.OP_PUSH) {
                    Logger.log("Block coinbase does not contain block height.");
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return false;
                }
                final PushOperation pushOperation = (PushOperation) operation;
                final Long coinbaseBlockHeight = pushOperation.getValue().asLong();
                if (blockHeight.longValue() != coinbaseBlockHeight.longValue()) {
                    Logger.log("Invalid block height within coinbase.");
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return false;
                }
            }
        }

        { // Validate coinbase input...
            final Transaction coinbaseTransaction = block.getCoinbaseTransaction();
            final List<TransactionInput> transactionInputs = coinbaseTransaction.getTransactionInputs();

            { // Validate transaction amount...
                if (transactionInputs.getSize() != 1) {
                    Logger.log("Invalid coinbase transaction inputs. Count: " + transactionInputs.getSize() + "; " + "Block: " + block.getHash());
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return false;
                }
            }

            { // Validate previousOutputTransactionHash...
                final Sha256Hash requiredHash = new ImmutableSha256Hash();
                final TransactionInput transactionInput = transactionInputs.get(0);
                final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                if (! requiredHash.equals(previousOutputTransactionHash)) {
                    Logger.log("Invalid coinbase transaction input. PreviousTransactionHash: " + previousOutputTransactionHash + "; " + "Block: " + block.getHash());
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return false;
                }
            }
        }

        final List<Long> expenditureResults = totalExpenditureValidationTaskSpawner.waitForResults();
        final List<Boolean> unlockedInputsResults = transactionValidationTaskSpawner.waitForResults();

        if (expenditureResults == null) {
            Logger.log("NOTICE: Expenditure validator returned null...");
            return false;
        }

        if (unlockedInputsResults == null) {
            Logger.log("NOTICE: Inputs validator returned null...");
            return false;
        }

        validateBlockTimer.stop();
        if (_shouldLogValidBlocks) {
            Logger.log("Validated " + transactions.getSize() + " transactions in " + (validateBlockTimer.getMillisecondsElapsed()) + "ms (" + ((int) ((transactions.getSize() / validateBlockTimer.getMillisecondsElapsed()) * 1000)) + " tps). " + block.getHash());
        }

        final Long totalTransactionFees;
        {
            long amount = 0L;
            for (final Long transactionFeeSubtotal : expenditureResults) {
                if (transactionFeeSubtotal == null) {
                    Logger.log("Block invalid due to invalid expenditures.");
                    return false;
                }
                amount += transactionFeeSubtotal;
            }
            totalTransactionFees = amount;
        }

        for (final Boolean value : unlockedInputsResults) {
            if (! value) {
                Logger.log("Block invalid due to unlocked inputs.");
                return false;
            }
        }

        { // Validate coinbase amount...
            final Transaction coinbaseTransaction = block.getCoinbaseTransaction();
            final Long maximumCreatedCoinsAmount = BlockHeader.calculateBlockReward(blockHeight);

            final Long maximumCoinbaseValue = (maximumCreatedCoinsAmount + totalTransactionFees);

            final Long coinbaseTransactionAmount;
            {
                long totalAmount = 0L;
                for (final TransactionOutput transactionOutput : coinbaseTransaction.getTransactionOutputs()) {
                    totalAmount += transactionOutput.getAmount();
                }
                coinbaseTransactionAmount = totalAmount;
            }

            final Boolean coinbaseTransactionAmountIsValid = (coinbaseTransactionAmount <= maximumCoinbaseValue);
            if (! coinbaseTransactionAmountIsValid) {
                Logger.log("Invalid coinbase transaction amount. Amount: " + coinbaseTransactionAmount + "; " + "Block: "+ block.getHash());
                return false;
            }
        }

        return true;
    }

    public BlockValidator(final MysqlDatabaseConnectionFactory threadedConnectionsFactory, final DatabaseManagerCache databaseManagerCache, final NetworkTime networkTime, final MedianBlockTimeWithBlocks medianBlockTime) {
        _databaseConnectionFactory = threadedConnectionsFactory;
        _databaseManagerCache = databaseManagerCache;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    /**
     *  Sets the total number of threads that will be spawned for each call to BlockValidator::Validate.  NOTE: This number should be divisible by 2.
     */
    public void setMaxThreadCount(final Integer maxThreadCount) {
        _maxThreadCount = maxThreadCount;
    }

    public void setTrustedBlockHeight(final Integer trustedBlockHeight) {
        _trustedBlockHeight = trustedBlockHeight;
    }

    public Boolean validateBlock(final BlockId blockId, final Block nullableBlock) {
        final Block block;
        final Long blockHeight;
        final BlockChainSegmentId blockChainSegmentId;
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

            if (nullableBlock != null) {
                block = nullableBlock;
                { // Validate BlockId...
                    final Sha256Hash blockHash = block.getHash();
                    final BlockId actualBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                    if (! Util.areEqual(actualBlockId, blockId)) {
                        Logger.log("ERROR: BlockId mismatch. " + blockId + " vs " + actualBlockId);
                        return false;
                    }
                }
            }
            else {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
                block = blockDatabaseManager.getBlock(blockId);
                if (block == null) {
                    Logger.log("No transactions for block id: " + blockId);
                    return false;
                }
            }

            blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

            final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(databaseConnection, _databaseManagerCache, _networkTime, _medianBlockTime);
            final Boolean headerIsValid = blockHeaderValidator.validateBlockHeader(block, blockHeight);
            if (! headerIsValid) {
                Logger.log("Invalid block. Header invalid.");
                return false;
            }

            blockChainSegmentId = blockHeaderDatabaseManager.getBlockChainSegmentId(blockId);
        }
        catch (final DatabaseException databaseException) {
            Logger.log("Error encountered validating block:");
            Logger.log(databaseException);
            return false;
        }

        return _validateBlock(blockChainSegmentId, block, blockHeight);
    }

    public Boolean validateBlockTransactions(final BlockId blockId, final Block nullableBlock) {
        final Block block;
        final Long blockHeight;
        final BlockChainSegmentId blockChainSegmentId;
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            blockChainSegmentId = blockHeaderDatabaseManager.getBlockChainSegmentId(blockId);

            if (nullableBlock != null) {
                block = nullableBlock;

                { // Validate BlockId...
                    final Sha256Hash blockHash = block.getHash();
                    final BlockId actualBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                    if (! Util.areEqual(actualBlockId, blockId)) {
                        Logger.log("ERROR: BlockId mismatch. " + blockId + " vs " + actualBlockId);
                        return false;
                    }
                }
            }
            else {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
                block = blockDatabaseManager.getBlock(blockId);
                if (block == null) {
                    Logger.log("No transactions for block id: " + blockId);
                    return false;
                }
            }
        }
        catch (final DatabaseException databaseException) {
            Logger.log("Error encountered validating block:");
            Logger.log(databaseException);
            return false;
        }

        return _validateBlock(blockChainSegmentId, block, blockHeight);
    }

    public void setShouldLogValidBlocks(final Boolean shouldLogValidBlocks) {
        _shouldLogValidBlocks = shouldLogValidBlocks;
    }
}

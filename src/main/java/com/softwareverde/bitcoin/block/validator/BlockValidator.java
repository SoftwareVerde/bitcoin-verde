package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.block.validator.thread.*;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.Map;

public class BlockValidator {
    protected final ReadUncommittedDatabaseConnectionFactory _databaseConnectionFactory;

    public BlockValidator(final ReadUncommittedDatabaseConnectionFactory threadedConnectionsFactory) {
        _databaseConnectionFactory = threadedConnectionsFactory;
    }

    public Boolean validateBlock(final BlockChainSegmentId blockChainSegmentId, final Block block) {
        if (! block.isValid()) { return false; }

        final long startTime = System.currentTimeMillis();

        final List<Transaction> transactions;
        final Map<Hash, Transaction> queuedTransactionOutputs = new HashMap<Hash, Transaction>();
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
                return new TotalExpenditureTaskHandler(blockChainSegmentId, queuedTransactionOutputs);
            }
        };

        final TaskHandlerFactory<Transaction, Boolean> unlockedInputsTaskHandlerFactory = new TaskHandlerFactory<Transaction, Boolean>() {
            @Override
            public TaskHandler<Transaction, Boolean> newInstance() {
                return new UnlockedInputsTaskHandler(blockChainSegmentId);
            }
        };

        // TODO: Validate block timestamp... (https://en.bitcoin.it/wiki/Block_timestamp)
        // TODO: Validate block size...
        // TODO: Validate max operations per block... (https://bitcoin.stackexchange.com/questions/35691/if-block-sizes-go-up-wont-sigop-limits-have-to-change-too)

        { // Validate coinbase input...
            final Transaction coinbaseTransaction = block.getCoinbaseTransaction();
            final List<TransactionInput> transactionInputs = coinbaseTransaction.getTransactionInputs();

            { // Validate transaction mount...
                if (transactionInputs.getSize() != 1) {
                    Logger.log("Invalid coinbase transaction inputs. Count: " + transactionInputs.getSize() + "; " + "Block: " + block.getHash());
                    return false;
                }
            }

            { // Validate previousOutputTransactionHash...
                final Hash requiredHash = new ImmutableHash();
                final TransactionInput transactionInput = transactionInputs.get(0);
                final Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                if (! requiredHash.equals(previousOutputTransactionHash)) {
                    Logger.log("Invalid coinbase transaction input. PreviousTransactionHash: " + previousOutputTransactionHash + "; " + "Block: " + block.getHash());
                    return false;
                }
            }
        }

        { // Validate block (calculated) difficulty...
            try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseConnection);
                final Difficulty calculatedRequiredDifficulty = difficultyCalculator.calculateRequiredDifficulty(blockChainSegmentId, block);
                if (calculatedRequiredDifficulty == null) {
                    Logger.log("Unable to calculate required difficulty for block: " + blockChainSegmentId + " " + block.getHash());
                    return false;
                }

                final Boolean difficultyIsCorrect = calculatedRequiredDifficulty.equals(block.getDifficulty());
                if (!difficultyIsCorrect) {
                    Logger.log("Invalid difficulty for block. Required: " + HexUtil.toHexString(calculatedRequiredDifficulty.encode()) + " Found: " + HexUtil.toHexString(block.getDifficulty().encode()));
                    return false;
                }
            }
            catch (final DatabaseException databaseException) {
                Logger.log(databaseException);
                return false;
            }
        }

        final ParallelledTaskSpawner<Transaction, Long> totalExpenditureValidationTaskSpawner = new ParallelledTaskSpawner<Transaction, Long>(_databaseConnectionFactory);
        totalExpenditureValidationTaskSpawner.setTaskHandlerFactory(totalExpenditureTaskHandlerFactory);
        totalExpenditureValidationTaskSpawner.executeTasks(transactions, 2);

        final ParallelledTaskSpawner<Transaction, Boolean> unlockedInputsValidationTaskSpawner = new ParallelledTaskSpawner<Transaction, Boolean>(_databaseConnectionFactory);
        unlockedInputsValidationTaskSpawner.setTaskHandlerFactory(unlockedInputsTaskHandlerFactory);
        unlockedInputsValidationTaskSpawner.executeTasks(transactions, 2);

        final List<Long> expenditureResults = totalExpenditureValidationTaskSpawner.waitForResults();
        final List<Boolean> unlockedInputsResults = unlockedInputsValidationTaskSpawner.waitForResults();

        final long endTime = System.currentTimeMillis();
        final long msElapsed = (endTime - startTime);
        Logger.log("Validated "+ transactions.getSize() + " transactions in " + (msElapsed) + "ms ("+ String.format("%.2f", ((((double) transactions.getSize()) / msElapsed) * 1000)) +" tps).");

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
            try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                final Transaction coinbaseTransaction = block.getCoinbaseTransaction();
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(block.getHash());
                final Long blockHeight = blockDatabaseManager.getBlockHeightForBlockId(blockId);
                final Long maximumCreatedCoinsAmount = ((50 * Transaction.SATOSHIS_PER_BITCOIN) >> (blockHeight / 210000));

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
            catch (final DatabaseException databaseException) {
                Logger.log(databaseException);
                return false;
            }
        }

        return true;
    }
}

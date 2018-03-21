package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.thread.*;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.factory.DatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

import java.util.HashMap;
import java.util.Map;

public class BlockValidator {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    protected Difficulty _calculateRequiredDifficulty(final BlockChainSegmentId blockChainSegmentId) {
        final Integer blockCountPerDifficultyAdjustment = 2016;
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
            final BlockChainSegment blockChainSegment = blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId);
            if (blockChainSegment.getBlockHeight() % blockCountPerDifficultyAdjustment == 0) {
                //  Calculate the new difficulty. https://bitcoin.stackexchange.com/questions/5838/how-is-difficulty-calculated
                //  1. Get the block that is 2016 blocks behind the head block of this chain.
                //  2. Get the current network time from the other nodes on the network.
                //  3. Calculate the difference between the network-time and the time of the 2015th-parent block ("secondsElapsed"). (NOTE: 2015 instead of 2016 due to protocol bug.)
                //  4. Calculate the ratio between the difference and the desired two-weeks elapse-time ("secondsInTwoWeeks").
                //  5. Calculate the difficulty adjustment via (secondsInTwoWeeks / secondsElapsed) ("difficultyAdjustment").
                //  6. Bound difficultyAdjustment between [4, 0.25].
                //  7. Multiply the difficulty by the bounded difficultyAdjustment.
                return Difficulty.BASE_DIFFICULTY; // TODO
            }
            else {
                final BlockHeader headBlockHeader = blockDatabaseManager.getBlockHeader(blockChainSegment.getHeadBlockId());
                return headBlockHeader.getDifficulty();
            }
        }
        catch (final DatabaseException exception) { }
        return null;
    }

    public BlockValidator(final DatabaseConnectionFactory threadedConnectionsFactory) {
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

        final TaskHandlerFactory<Transaction, Boolean> totalExpenditureTaskHandlerFactory = new TaskHandlerFactory<Transaction, Boolean>() {
            @Override
            public TaskHandler<Transaction, Boolean> newInstance() {
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

        { // TODO: Validate block (calculated) difficulty... (https://bitcoin.stackexchange.com/questions/5838/how-is-difficulty-calculated)
            final Difficulty calculatedRequiredDifficulty = _calculateRequiredDifficulty(blockChainSegmentId);
            final Boolean difficultyIsCorrect = calculatedRequiredDifficulty.equals(block.getDifficulty());
            if (! difficultyIsCorrect) {
                Logger.log("Invalid difficulty for block. Required: " + HexUtil.toHexString(calculatedRequiredDifficulty.encode()));
                return false;
            }
        }

        // TODO: Validate coinbase transaction...

        final ParallelledTaskSpawner<Transaction, Boolean> totalExpenditureValidationTaskSpawner = new ParallelledTaskSpawner<Transaction, Boolean>(_databaseConnectionFactory);
        totalExpenditureValidationTaskSpawner.setTaskHandler(totalExpenditureTaskHandlerFactory);
        totalExpenditureValidationTaskSpawner.executeTasks(transactions, 2);

        final ParallelledTaskSpawner<Transaction, Boolean> unlockedInputsValidationTaskSpawner = new ParallelledTaskSpawner<Transaction, Boolean>(_databaseConnectionFactory);
        unlockedInputsValidationTaskSpawner.setTaskHandler(unlockedInputsTaskHandlerFactory);
        unlockedInputsValidationTaskSpawner.executeTasks(transactions, 2);

        final List<Boolean> expenditureResults = totalExpenditureValidationTaskSpawner.waitForResults();
        final List<Boolean> unlockedInputsResults = unlockedInputsValidationTaskSpawner.waitForResults();

        final long endTime = System.currentTimeMillis();
        final long msElapsed = (endTime - startTime);
        Logger.log("Validated "+ transactions.getSize() + " transactions in " + (msElapsed) + "ms ("+ String.format("%.2f", ((((double) transactions.getSize()) / msElapsed) * 1000)) +" tps).");

        for (final Boolean value : expenditureResults) {
            if (! value) {
                Logger.log("Block invalid due to invalid expenditures.");
                return false;
            }
        }

        for (final Boolean value : unlockedInputsResults) {
            if (! value) {
                Logger.log("Block invalid due to unlocked inputs.");
                return false;
            }
        }

        return true;
    }
}

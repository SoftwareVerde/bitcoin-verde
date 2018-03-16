package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.validator.thread.*;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.embedded.factory.DatabaseConnectionFactory;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.Map;

public class BlockValidator {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public BlockValidator(final DatabaseConnectionFactory threadedConnectionsFactory) {
        _databaseConnectionFactory = threadedConnectionsFactory;
    }

    public Boolean validateBlock(final BlockChainSegmentId blockChainSegmentId, final Block block) throws DatabaseException {
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
            if (! value) { return false; }
        }

        for (final Boolean value : unlockedInputsResults) {
            if (! value) { return false; }
        }

        return true;
    }
}

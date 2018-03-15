package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.validator.thread.ParallelledTaskSpawner;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.factory.DatabaseConnectionFactory;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.Map;

public class BlockValidator {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    protected static TransactionOutput _findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionDatabaseManager transactionDatabaseManager, final TransactionOutputDatabaseManager transactionOutputDatabaseManager) {
        try {
            final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
            final TransactionId transactionId = transactionDatabaseManager.getTransactionIdFromHash(transactionOutputIdentifier.getBlockChainSegmentId(), transactionOutputIdentifier.getTransactionHash());
            if (transactionId == null) { return null; }

            final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(transactionId, transactionOutputIndex);
            if (transactionOutputId == null) { return null; }

            return transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    protected static Long _calculateTotalTransactionInputs(final BlockChainSegmentId blockChainSegmentId, final Transaction transaction, final Map<Hash, Transaction> queuedTransactions, final MysqlDatabaseConnection databaseConnection) {
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);

        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);

            final Hash outputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final Integer transactionOutputIndex = transactionInput.getPreviousOutputIndex();

            final TransactionOutput transactionOutput;
            {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(blockChainSegmentId, outputTransactionHash, transactionOutputIndex);
                TransactionOutput possibleTransactionOutput = _findTransactionOutput(transactionOutputIdentifier, transactionDatabaseManager, transactionOutputDatabaseManager);
                if (possibleTransactionOutput == null) {
                    final Transaction transactionContainingOutput = queuedTransactions.get(outputTransactionHash);
                    if (transactionContainingOutput != null) {
                        final List<TransactionOutput> transactionOutputs = transactionContainingOutput.getTransactionOutputs();
                        if (transactionOutputIndex < transactionOutputs.getSize()) {
                            possibleTransactionOutput = transactionOutputs.get(transactionOutputIndex);
                        }
                    }
                }
                transactionOutput = possibleTransactionOutput;
            }
            if (transactionOutput == null) {
                Logger.log("Tx Input, Output Not Found: " + BitcoinUtil.toHexString(outputTransactionHash) + ":" + transactionOutputIndex);
                totalInputValue = -1L;
                break;
            }

            totalInputValue += transactionOutput.getAmount();
        }

        return totalInputValue;
    }

    public BlockValidator(final DatabaseConnectionFactory threadedConnectionsFactory) {
        _databaseConnectionFactory = threadedConnectionsFactory;
    }

    public Boolean validateBlock(final BlockChainSegmentId blockChainSegmentId, final Block block) throws DatabaseException {
        if (! block.isValid()) { return false; }

        final List<Transaction> transactions;
        final Map<Hash, Transaction> additionalTransactionOutputs = new HashMap<Hash, Transaction>();
        { // Remove the coinbase transaction and create a lookup map for transaction outputs...
            final List<Transaction> fullTransactionList = block.getTransactions();
            final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(fullTransactionList.getSize());
            int transactionIndex = 0;
            for (final Transaction transaction : fullTransactionList) {
                if (transactionIndex > 0) {
                    listBuilder.add(transaction);
                }

                additionalTransactionOutputs.put(transaction.getHash(), transaction);

                transactionIndex += 1;
            }
            transactions = listBuilder.build();
        }

        final ParallelledTaskSpawner.TaskHandler<Transaction, Boolean> totalExpenditureTaskHandler = new ParallelledTaskSpawner.TaskHandler<Transaction, Boolean>() {
            protected MysqlDatabaseConnection _databaseConnection;
            protected boolean _allTransactionsExpendituresAreValid = true;

            @Override
            public void init(final MysqlDatabaseConnection databaseConnection) {
                _databaseConnection = databaseConnection;
            }

            @Override
            public void executeTask(final Transaction transaction) {
                if (! _allTransactionsExpendituresAreValid) { return; }

                final Long totalOutputValue = transaction.getTotalOutputValue();
                final Long totalInputValue = _calculateTotalTransactionInputs(blockChainSegmentId, transaction, additionalTransactionOutputs, _databaseConnection);

                final boolean transactionExpenditureIsValid = ( (totalInputValue != null) && (totalOutputValue <= totalInputValue) );
                if (! transactionExpenditureIsValid) {
                    _allTransactionsExpendituresAreValid = false;
                }
            }

            @Override
            public Boolean getResult() {
                return _allTransactionsExpendituresAreValid;
            }
        };

        final ParallelledTaskSpawner.TaskHandler<Transaction, Boolean> unlockedInputsTaskHandler = new ParallelledTaskSpawner.TaskHandler<Transaction, Boolean>() {
            private TransactionValidator _transactionValidator;
            private boolean _allInputsAreUnlocked = true;

            @Override
            public void init(final MysqlDatabaseConnection databaseConnection) {
                _transactionValidator = new TransactionValidator(databaseConnection);
            }

            @Override
            public void executeTask(final Transaction transaction) {
                if (! _allInputsAreUnlocked) { return; }

                final boolean inputsAreUnlocked = _transactionValidator.validateTransactionInputsAreUnlocked(blockChainSegmentId, transaction);
                if (! inputsAreUnlocked) {
                    _allInputsAreUnlocked = false;
                }
            }

            @Override
            public Boolean getResult() {
                return _allInputsAreUnlocked;
            }
        };

        final ParallelledTaskSpawner<Transaction> totalExpenditureValidationTaskSpawner = new ParallelledTaskSpawner<Transaction>(_databaseConnectionFactory);
        totalExpenditureValidationTaskSpawner.setTaskHandler(totalExpenditureTaskHandler);
        totalExpenditureValidationTaskSpawner.executeTasks(transactions, 8);

        final ParallelledTaskSpawner<Transaction> unlockedInputsValidationTaskSpawner = new ParallelledTaskSpawner<Transaction>(_databaseConnectionFactory);
        unlockedInputsValidationTaskSpawner.setTaskHandler(unlockedInputsTaskHandler);
        unlockedInputsValidationTaskSpawner.executeTasks(transactions, 8);

        totalExpenditureValidationTaskSpawner.waitUntilComplete();
        unlockedInputsValidationTaskSpawner.waitUntilComplete();

        return (totalExpenditureTaskHandler.getResult() && unlockedInputsTaskHandler.getResult());
    }
}

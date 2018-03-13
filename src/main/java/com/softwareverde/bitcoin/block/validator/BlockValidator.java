package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
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
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.factory.DatabaseConnectionFactory;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.Map;

public class BlockValidator {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final TransactionValidator _transactionValidator;

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

    protected static class InputCalculationThread extends Thread {
        protected final TransactionDatabaseManager _transactionDatabaseManager;
        protected final TransactionOutputDatabaseManager _transactionOutputDatabaseManager;

        protected long _totalInputValue = 0L;

        protected int _beginningInputIndex = -1;
        protected int _inputCount = -1;
        protected List<TransactionInput> _transactionInputs = null;
        protected Map<Hash, Transaction> _additionalTransactionOutputs = null;
        protected BlockChainSegmentId _blockChainSegmentId = null;

        public InputCalculationThread(final MysqlDatabaseConnection databaseConnection) {
            _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
            _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);
        }

        public void setBlockChainSegmentId(final BlockChainSegmentId blockChainSegmentId) {
            _blockChainSegmentId = blockChainSegmentId;
        }

        public void setTransactionInputs(final List<TransactionInput> transactionInputs) {
            _transactionInputs = transactionInputs;
        }

        public void setInputStartIndex(final int beginningInputIndex) {
            _beginningInputIndex = beginningInputIndex;
        }

        public void setInputCount(final int inputCount) {
            _inputCount = inputCount;
        }

        public void setAdditionalTransactionOutputs(final Map<Hash, Transaction> additionalTransactionOutputs) {
            _additionalTransactionOutputs = additionalTransactionOutputs;
        }

        @Override
        public void run() {
            for (int i=0; i<_inputCount; ++i) {
                final TransactionInput transactionInput = _transactionInputs.get(i + _beginningInputIndex);

                final Hash outputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                final Integer transactionOutputIndex = transactionInput.getPreviousOutputIndex();

                final TransactionOutput transactionOutput;
                {
                    TransactionOutput possibleTransactionOutput = _findTransactionOutput(new TransactionOutputIdentifier(_blockChainSegmentId, outputTransactionHash, transactionOutputIndex), _transactionDatabaseManager, _transactionOutputDatabaseManager);
                    if (possibleTransactionOutput == null) {
                        final Transaction transactionContainingOutput = _additionalTransactionOutputs.get(outputTransactionHash);
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
                    _totalInputValue = -1L;
                    return;
                }

                _totalInputValue += transactionOutput.getAmount();
            }
        }

        public Long getTotalInputValue() {
            return (_totalInputValue < 0 ? null : _totalInputValue);
        }
    }

    protected Long _calculateTotalTransactionInputs(final BlockChainSegmentId blockChainId, final Transaction blockTransaction, final List<Transaction> queuedTransactions) {
        final Map<Hash, Transaction> additionalTransactionOutputs = new HashMap<Hash, Transaction>();
        for (final Transaction transaction : queuedTransactions) {
            additionalTransactionOutputs.put(transaction.getHash(), transaction);
        }

        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = blockTransaction.getTransactionInputs();
        if (transactionInputs.getSize() < 8) {
            Logger.log(transactionInputs.getSize() + " inputs. Using single thread.");

            final MysqlDatabaseConnection databaseConnection;
            try {
                databaseConnection = _databaseConnectionFactory.newConnection();
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                return null;
            }

            final InputCalculationThread inputCalculationThread = new InputCalculationThread(databaseConnection);
            inputCalculationThread.setBlockChainSegmentId(blockChainId);
            inputCalculationThread.setTransactionInputs(transactionInputs);
            inputCalculationThread.setInputStartIndex(0);
            inputCalculationThread.setInputCount(transactionInputs.getSize());
            inputCalculationThread.setAdditionalTransactionOutputs(additionalTransactionOutputs);

            inputCalculationThread.run();
            totalInputValue += inputCalculationThread.getTotalInputValue();
        }
        else {
            final MutableList<InputCalculationThread> inputCalculationThreads = new MutableList<InputCalculationThread>();

            final int threadCount = Math.min(8, (transactionInputs.getSize() / 8));
            final int itemsPerThread = (transactionInputs.getSize() / threadCount);

            final MysqlDatabaseConnection[] mysqlDatabaseConnections = new MysqlDatabaseConnection[threadCount];
            for (int i=0; i<threadCount; ++i) {
                try {
                    final MysqlDatabaseConnection mysqlDatabaseConnection = _databaseConnectionFactory.newConnection();
                    mysqlDatabaseConnection.executeSql(new Query("SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED"));
                    mysqlDatabaseConnections[i] = mysqlDatabaseConnection;
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    return null;
                }
            }

            for (int i=0; i<threadCount; ++i) {
                final int startIndex = i * itemsPerThread;
                final int remainingItems = (transactionInputs.getSize() - startIndex);
                final int inputCount = ( (i < (threadCount - 1)) ? Math.min(itemsPerThread, remainingItems) : remainingItems);

                final InputCalculationThread thread = new InputCalculationThread(mysqlDatabaseConnections[i]);
                thread.setBlockChainSegmentId(blockChainId);
                thread.setTransactionInputs(transactionInputs);
                thread.setInputStartIndex(startIndex);
                thread.setInputCount(inputCount);
                thread.setAdditionalTransactionOutputs(additionalTransactionOutputs);

                Logger.log(transactionInputs.getSize() + " inputs. Spawning thread: "+ i +" :: "+ startIndex +" - "+ (startIndex + inputCount));
                thread.start();
                inputCalculationThreads.add(thread);
            }

            for (final InputCalculationThread inputCalculationThread : inputCalculationThreads) {
                try { inputCalculationThread.join(); } catch (InterruptedException exception) { }
                final Long threadInputValue = inputCalculationThread.getTotalInputValue();
                if (threadInputValue == null) {
                    Logger.log("Unable to calculate Tx Total: "+ blockTransaction.getHash());
                    return null;
                }
                totalInputValue += threadInputValue;
            }
        }
        return totalInputValue;
    }

    protected Boolean _validateTransactionExpenditure(final BlockChainSegmentId blockChainSegmentId, final Transaction blockTransaction, final List<Transaction> queuedTransactions) {
        final Long totalOutputValue = blockTransaction.getTotalOutputValue();
        final Long totalInputValue = _calculateTotalTransactionInputs(blockChainSegmentId, blockTransaction, queuedTransactions);
        if (totalInputValue == null) { return false; }

        return (totalOutputValue <= totalInputValue);
    }

    public BlockValidator(final MysqlDatabaseConnection mainDatabaseConnection, final DatabaseConnectionFactory threadedConnectionsFactory) {
        _databaseConnectionFactory = threadedConnectionsFactory;
        _transactionValidator = new TransactionValidator(mainDatabaseConnection);
    }

    public Boolean validateBlock(final BlockChainSegmentId blockChainSegmentId, final Block block) throws DatabaseException {
        if (! block.isValid()) { return false; }

        final BlockDeflater blockDeflater = new BlockDeflater();

        final List<Transaction> blockTransactions = block.getTransactions();
        for (int i=0; i<blockTransactions.getSize(); ++i) {
            if (i == 0) { continue; } // TODO: The coinbase transaction requires a separate validation process...

            final Transaction blockTransaction = blockTransactions.get(i);
            final Boolean transactionExpenditureIsValid = _validateTransactionExpenditure(blockChainSegmentId, blockTransaction, blockTransactions);
            if (! transactionExpenditureIsValid) {
                Logger.log("BLOCK VALIDATION: Failed because expenditures did not match.");
                Logger.log(BitcoinUtil.toHexString(blockDeflater.toBytes(block)));
                return false;
            }

            final Boolean transactionInputsAreUnlocked = _transactionValidator.validateTransactionInputsAreUnlocked(blockChainSegmentId, blockTransaction);
            if (! transactionInputsAreUnlocked) {
                Logger.log("BLOCK VALIDATION: Failed because of invalid transaction.");
                Logger.log(BitcoinUtil.toHexString(blockDeflater.toBytes(block)));
                return false;
            }
        }

        return true;
    }
}

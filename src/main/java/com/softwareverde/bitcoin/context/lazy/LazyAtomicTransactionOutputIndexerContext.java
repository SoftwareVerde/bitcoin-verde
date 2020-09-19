package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputId;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.NanoTimer;

public class LazyAtomicTransactionOutputIndexerContext implements AtomicTransactionOutputIndexerContext {
    protected static class QueuedOutputs {
        public final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>();
        public final MutableList<Integer> outputIndexes = new MutableList<Integer>();
        public final MutableList<Long> amounts = new MutableList<Long>();
        public final MutableList<ScriptType> scriptTypes = new MutableList<ScriptType>();
        public final MutableList<Address> addresses = new MutableList<Address>();
        public final MutableList<TransactionId> slpTransactionIds = new MutableList<TransactionId>();
    }

    protected static class QueuedInputs {
        public final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>();
        public final MutableList<Integer> inputIndexes = new MutableList<Integer>();
        public final MutableList<TransactionOutputId> transactionOutputIds = new MutableList<TransactionOutputId>();
    }

    protected final FullNodeDatabaseManager _databaseManager;
    protected final QueuedInputs _queuedInputs = new QueuedInputs();
    protected final QueuedOutputs _queuedOutputs = new QueuedOutputs();

    protected Double _storeAddressMs = 0D;
    protected Double _getUnprocessedTransactionsMs = 0D;
    protected Double _dequeueTransactionsForProcessingMs = 0D;
    protected Double _getTransactionIdMs = 0D;
    protected Double _getTransactionMs = 0D;
    protected Double _indexTransactionOutputMs = 0D;
    protected Double _indexTransactionInputMs = 0D;

    protected TransactionId _getTransactionId(final Sha256Hash transactionHash) throws ContextException {
        try {
            final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();
            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            nanoTimer.stop();
            _getTransactionIdMs += nanoTimer.getMillisecondsElapsed();
            return transactionId;
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    protected Transaction _getTransaction(final TransactionId transactionId) throws ContextException {
        try {
            final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
            nanoTimer.stop();
            _getTransactionMs += nanoTimer.getMillisecondsElapsed();
            return transaction;
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    public LazyAtomicTransactionOutputIndexerContext(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    @Override
    public void startDatabaseTransaction() throws ContextException {
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            TransactionUtil.startTransaction(databaseConnection);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public void commitDatabaseTransaction() throws ContextException {
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
            {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                blockchainIndexerDatabaseManager.indexTransactionOutputs(_queuedOutputs.transactionIds, _queuedOutputs.outputIndexes, _queuedOutputs.amounts, _queuedOutputs.scriptTypes, _queuedOutputs.addresses, _queuedOutputs.slpTransactionIds);
                nanoTimer.stop();
                _indexTransactionOutputMs += nanoTimer.getMillisecondsElapsed();
            }

            {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                blockchainIndexerDatabaseManager.indexTransactionInputs(_queuedInputs.transactionIds, _queuedInputs.inputIndexes, _queuedInputs.transactionOutputIds);
                nanoTimer.stop();
                _indexTransactionInputMs += nanoTimer.getMillisecondsElapsed();
            }

            TransactionUtil.commitTransaction(databaseConnection);

            Logger.trace("_storeAddressMs=" + _storeAddressMs + "ms, _getUnprocessedTransactionsMs=" + _getUnprocessedTransactionsMs + "ms, _dequeueTransactionsForProcessingMs=" + _dequeueTransactionsForProcessingMs + "ms, _getTransactionIdMs=" + _getTransactionIdMs + "ms, _getTransactionMs=" + _getTransactionMs + "ms, _indexTransactionOutputMs=" + _indexTransactionOutputMs + "ms, _indexTransactionInputMs=" + _indexTransactionInputMs + "ms");
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public void rollbackDatabaseTransaction() {
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            TransactionUtil.rollbackTransaction(databaseConnection);
        }
        catch (final DatabaseException databaseException) {
            Logger.debug(databaseException);
        }
    }

    @Override
    public List<TransactionId> getUnprocessedTransactions(final Integer batchSize) throws ContextException {
        try {
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();
            final List<TransactionId> transactionIds = blockchainIndexerDatabaseManager.getUnprocessedTransactions(batchSize);
            nanoTimer.stop();
            _getUnprocessedTransactionsMs += nanoTimer.getMillisecondsElapsed();
            return transactionIds;
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public void dequeueTransactionsForProcessing(final List<TransactionId> transactionIds) throws ContextException {
        try {
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();
            blockchainIndexerDatabaseManager.dequeueTransactionsForProcessing(transactionIds);
            nanoTimer.stop();
            _dequeueTransactionsForProcessingMs += nanoTimer.getMillisecondsElapsed();
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws ContextException {
        return _getTransactionId(transactionHash);
    }

    @Override
    public TransactionId getTransactionId(final SlpTokenId slpTokenId) throws ContextException {
        return _getTransactionId(slpTokenId);
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) throws ContextException {
        return _getTransaction(transactionId);
    }

    @Override
    public void indexTransactionOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final Address address, final TransactionId slpTransactionId) throws ContextException {
        _queuedOutputs.transactionIds.add(transactionId);
        _queuedOutputs.outputIndexes.add(outputIndex);
        _queuedOutputs.amounts.add(amount);
        _queuedOutputs.scriptTypes.add(scriptType);
        _queuedOutputs.addresses.add(address);
        _queuedOutputs.slpTransactionIds.add(slpTransactionId);
    }

    @Override
    public void indexTransactionInput(final TransactionId transactionId, final Integer inputIndex, final TransactionOutputId transactionOutputId) throws ContextException {
        _queuedInputs.transactionIds.add(transactionId);
        _queuedInputs.inputIndexes.add(inputIndex);
        _queuedInputs.transactionOutputIds.add(transactionOutputId);
    }

    @Override
    public void close() throws ContextException {
        try {
            _databaseManager.close();
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }
}

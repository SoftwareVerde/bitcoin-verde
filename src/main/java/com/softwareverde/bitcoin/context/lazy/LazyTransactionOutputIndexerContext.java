package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;

public class LazyTransactionOutputIndexerContext implements TransactionOutputIndexerContext {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected FullNodeDatabaseManager _databaseManager;

    public LazyTransactionOutputIndexerContext(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public AutoCloseable startDatabaseTransaction() throws ContextException {
        try {
            final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager();
            _databaseManager = databaseManager;
            return databaseManager;
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public void commitDatabaseTransaction() throws ContextException {
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            TransactionUtil.commitTransaction(databaseConnection);
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
    public AddressId getAddressId(final Address address) throws ContextException {
        try {
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
            return transactionOutputDatabaseManager.getAddressId(address);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public AddressId storeAddress(final Address address) throws ContextException {
        try {
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
            return transactionOutputDatabaseManager.storeAddress(address);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public List<TransactionId> getUnprocessedTransactions(final Integer batchSize) throws ContextException {
        try {
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
            return transactionOutputDatabaseManager.getUnprocessedTransactions(batchSize);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public void dequeueTransactionsForProcessing(final List<TransactionId> transactionIds) throws ContextException {
        try {
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
            transactionOutputDatabaseManager.dequeueTransactionsForProcessing(transactionIds);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public TransactionId getTransactionId(final SlpTokenId slpTokenId) throws ContextException {
        try {
            final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getTransactionId(slpTokenId);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) throws ContextException {
        try {
            final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getTransaction(transactionId);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public void indexTransactionOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final AddressId addressId, final TransactionId slpTransactionId) throws ContextException {
        try {
            final TransactionOutputDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
            transactionDatabaseManager.indexTransactionOutput(transactionId, outputIndex, amount, scriptType, addressId, slpTransactionId);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }
}

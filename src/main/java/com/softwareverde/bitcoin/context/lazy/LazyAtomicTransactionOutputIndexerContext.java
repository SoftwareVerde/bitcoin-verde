package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class LazyAtomicTransactionOutputIndexerContext implements AtomicTransactionOutputIndexerContext {
    protected FullNodeDatabaseManager _databaseManager;

    public LazyAtomicTransactionOutputIndexerContext(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;

        try {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            TransactionUtil.startTransaction(databaseConnection);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
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
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
            return blockchainIndexerDatabaseManager.getAddressId(address);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public AddressId storeAddress(final Address address) throws ContextException {
        try {
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
            return blockchainIndexerDatabaseManager.storeAddress(address);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public List<TransactionId> getUnprocessedTransactions(final Integer batchSize) throws ContextException {
        try {
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
            return blockchainIndexerDatabaseManager.getUnprocessedTransactions(batchSize);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public void dequeueTransactionsForProcessing(final List<TransactionId> transactionIds) throws ContextException {
        try {
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
            blockchainIndexerDatabaseManager.dequeueTransactionsForProcessing(transactionIds);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws ContextException {
        try {
            final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getTransactionId(transactionHash);
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
            final BlockchainIndexerDatabaseManager indexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
            indexerDatabaseManager.indexTransactionOutput(transactionId, outputIndex, amount, scriptType, addressId, slpTransactionId);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
    }

    @Override
    public void indexTransactionInput(final TransactionId transactionId, final Integer inputIndex, final AddressId addressId) throws ContextException {
        try {
            final BlockchainIndexerDatabaseManager indexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
            indexerDatabaseManager.indexTransactionInput(transactionId, inputIndex, addressId);
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
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

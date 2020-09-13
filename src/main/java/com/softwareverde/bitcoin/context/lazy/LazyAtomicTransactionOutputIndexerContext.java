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
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;

public class LazyAtomicTransactionOutputIndexerContext implements AtomicTransactionOutputIndexerContext {

    protected static class QueuedOutputs {
        public final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>();
        public final MutableList<Integer> outputIndexes = new MutableList<Integer>();
        public final MutableList<Long> amounts = new MutableList<Long>();
        public final MutableList<ScriptType> scriptTypes = new MutableList<ScriptType>();
        public final MutableList<AddressId> addressIds = new MutableList<AddressId>();
        public final MutableList<TransactionId> slpTransactionIds = new MutableList<TransactionId>();
    }

    protected static class QueuedInputs {
        public final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>();
        public final MutableList<Integer> inputIndexes = new MutableList<Integer>();
        public final MutableList<AddressId> addressIds = new MutableList<AddressId>();
    }

    protected final FullNodeDatabaseManager _databaseManager;
    protected final QueuedInputs _queuedInputs = new QueuedInputs();
    protected final QueuedOutputs _queuedOutputs = new QueuedOutputs();

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
            blockchainIndexerDatabaseManager.indexTransactionOutputs(_queuedOutputs.transactionIds, _queuedOutputs.outputIndexes, _queuedOutputs.amounts, _queuedOutputs.scriptTypes, _queuedOutputs.addressIds, _queuedOutputs.slpTransactionIds);
            blockchainIndexerDatabaseManager.indexTransactionInputs(_queuedInputs.transactionIds, _queuedInputs.inputIndexes, _queuedInputs.addressIds);

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
    public void indexTransactionOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final AddressId addressId, final TransactionId slpTransactionId) {
        _queuedOutputs.transactionIds.add(transactionId);
        _queuedOutputs.outputIndexes.add(outputIndex);
        _queuedOutputs.amounts.add(amount);
        _queuedOutputs.scriptTypes.add(scriptType);
        _queuedOutputs.addressIds.add(addressId);
        _queuedOutputs.slpTransactionIds.add(slpTransactionId);
    }

    @Override
    public void indexTransactionInput(final TransactionId transactionId, final Integer inputIndex, final AddressId addressId) {
        _queuedInputs.transactionIds.add(transactionId);
        _queuedInputs.inputIndexes.add(inputIndex);
        _queuedInputs.addressIds.add(addressId);
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

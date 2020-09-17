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
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;
import com.softwareverde.util.type.identifier.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LazyAtomicTransactionOutputIndexerContext implements AtomicTransactionOutputIndexerContext {

    public static class FutureAddressId extends AddressId {
        protected final Object _pin = new Object();
        protected Long _value;

        protected void _requireValue() {
            synchronized (_pin) {
                if (_value == null) {
                    try {
                        _pin.wait();
                    }
                    catch (final InterruptedException exception) {
                        throw new RuntimeException(exception);
                    }
                }

                if (_value == null) {
                    throw new RuntimeException("Value failed to load.");
                }
            }
        }

        public FutureAddressId() {
            super(0L);
        }

        public void setValue(final Long value) {
            synchronized (_pin) {
                _value = value;
                _pin.notifyAll();
            }
        }

        @Override
        public long longValue() {
            _requireValue();
            return _value;
        }

        @Override
        public int hashCode() {
            _requireValue();
            return _value.hashCode();
        }

        @Override
        public boolean equals(final Object object) {
            _requireValue();
            if (object instanceof Identifier) {
                final Identifier identifier = (Identifier) object;
                return Util.areEqual(_value, identifier.longValue());
            }

            return Util.areEqual(_value, object);
        }

        @Override
        public String toString() {
            _requireValue();
            return _value.toString();
        }

        @Override
        public int compareTo(final Identifier value) {
            _requireValue();
            return _value.compareTo(value.longValue());
        }
    }

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

            {
                final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();

                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                final Map<Address, AddressId> addressIds = blockchainIndexerDatabaseManager.storeAddresses(new ImmutableList<Address>(_futureAddresses.keySet()));
                for (final Address address : _futureAddresses.keySet()) {
                    final AddressId addressId = addressIds.get(address);
                    final FutureAddressId futureAddressId = _futureAddresses.get(address);
                    futureAddressId.setValue(addressId.longValue());
                }
                nanoTimer.stop();
                _storeAddressMs += nanoTimer.getMillisecondsElapsed();
            }

            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
            {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                blockchainIndexerDatabaseManager.indexTransactionOutputs(_queuedOutputs.transactionIds, _queuedOutputs.outputIndexes, _queuedOutputs.amounts, _queuedOutputs.scriptTypes, _queuedOutputs.addressIds, _queuedOutputs.slpTransactionIds);
                nanoTimer.stop();
                _indexTransactionOutputMs += nanoTimer.getMillisecondsElapsed();
            }

            {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                blockchainIndexerDatabaseManager.indexTransactionInputs(_queuedInputs.transactionIds, _queuedInputs.inputIndexes, _queuedInputs.addressIds);
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

    protected ConcurrentHashMap<Address, FutureAddressId> _futureAddresses = new ConcurrentHashMap<Address, FutureAddressId>();

    @Override
    public AddressId storeAddress(final Address address) throws ContextException {
        final FutureAddressId existingFutureAddressId = _futureAddresses.get(address);
        if (existingFutureAddressId != null) { return existingFutureAddressId; }

        final FutureAddressId futureAddressId = new FutureAddressId();
        _futureAddresses.put(address, futureAddressId);
        return futureAddressId;
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
    public void indexTransactionOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final AddressId addressId, final TransactionId slpTransactionId) throws ContextException {
        _queuedOutputs.transactionIds.add(transactionId);
        _queuedOutputs.outputIndexes.add(outputIndex);
        _queuedOutputs.amounts.add(amount);
        _queuedOutputs.scriptTypes.add(scriptType);
        _queuedOutputs.addressIds.add(addressId);
        _queuedOutputs.slpTransactionIds.add(slpTransactionId);
    }

    @Override
    public void indexTransactionInput(final TransactionId transactionId, final Integer inputIndex, final AddressId addressId) throws ContextException {
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

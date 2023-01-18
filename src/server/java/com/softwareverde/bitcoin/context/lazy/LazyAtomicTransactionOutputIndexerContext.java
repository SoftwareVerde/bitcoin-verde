package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.context.IndexerCache;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputId;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.util.TreeMap;

public class LazyAtomicTransactionOutputIndexerContext implements AtomicTransactionOutputIndexerContext {
    protected static class QueuedOutputs {
        public final MutableList<TransactionId> transactionIds = new MutableArrayList<>();
        public final MutableList<Integer> outputIndexes = new MutableArrayList<>();
        public final MutableList<Long> amounts = new MutableArrayList<>();
        public final MutableList<ScriptType> scriptTypes = new MutableArrayList<>();
        public final MutableList<Address> addresses = new MutableArrayList<>();
        public final MutableList<Sha256Hash> scriptHashes = new MutableArrayList<>();
        public final MutableList<TransactionId> slpTransactionIds = new MutableArrayList<>();
        public final MutableList<ByteArray> memoActionTypes = new MutableArrayList<>();
        public final MutableList<ByteArray> memoActionIdentifiers = new MutableArrayList<>();

        public void clear() {
            this.transactionIds.clear();
            this.outputIndexes.clear();
            this.amounts.clear();
            this.scriptTypes.clear();
            this.addresses.clear();
            this.scriptHashes.clear();
            this.slpTransactionIds.clear();
            this.memoActionTypes.clear();
            this.memoActionIdentifiers.clear();
        }
    }

    protected static class QueuedInputs {
        public final MutableList<TransactionId> transactionIds = new MutableArrayList<>();
        public final MutableList<Integer> inputIndexes = new MutableArrayList<>();
        public final MutableList<TransactionOutputId> transactionOutputIds = new MutableArrayList<>();

        public void clear() {
            this.transactionIds.clear();
            this.inputIndexes.clear();
            this.transactionOutputIds.clear();
        }
    }

    protected final FullNodeDatabaseManager _databaseManager;
    protected final IndexerCache _indexerCache;
    protected final Integer _cacheIdentifier;
    protected final QueuedInputs _queuedInputs = new QueuedInputs();
    protected final QueuedOutputs _queuedOutputs = new QueuedOutputs();
    protected TransactionId _greatestProcessedTransactionId = null;

    protected Double _getUnprocessedTransactionsMs = 0D;
    protected Double _dequeueTransactionsForProcessingMs = 0D;
    protected Double _getTransactionIdMs = 0D;
    protected Double _getTransactionMs = 0D;
    protected Double _indexTransactionOutputMs = 0D;
    protected Double _indexTransactionInputMs = 0D;

    protected TransactionId _getTransactionId(final Sha256Hash transactionHash) throws ContextException {
        final TransactionId cachedTransactionId = _indexerCache.getTransactionId(transactionHash);
        if (cachedTransactionId != null) { return cachedTransactionId; }

        try {
            final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();
            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId != null) {
                _indexerCache.cacheTransactionId(_cacheIdentifier, transactionHash, transactionId);
            }
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

    protected void _clear() {
        _queuedInputs.clear();
        _queuedOutputs.clear();
        _greatestProcessedTransactionId = null;

        _getUnprocessedTransactionsMs = 0D;
        _dequeueTransactionsForProcessingMs = 0D;
        _getTransactionIdMs = 0D;
        _getTransactionMs = 0D;
        _indexTransactionOutputMs = 0D;
        _indexTransactionInputMs = 0D;
    }

    public LazyAtomicTransactionOutputIndexerContext(final FullNodeDatabaseManager databaseManager, final IndexerCache indexerCache, final Integer cacheIdentifier) {
        _databaseManager = databaseManager;
        _indexerCache = indexerCache;
        _cacheIdentifier = cacheIdentifier;
    }

    @Override
    public void initialize() throws ContextException { }

    @Override
    public TransactionId finish() throws ContextException {
        try {
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
            {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();

                final QueuedOutputs queuedOutputs = new QueuedOutputs();
                { // Sort the items...
                    final int itemCount = _queuedOutputs.transactionIds.getCount();
                    final TreeMap<TransactionOutputId, Integer> treeMap = new TreeMap<>();
                    for (int i = 0; i < itemCount; ++i) {
                        final TransactionId transactionId = _queuedOutputs.transactionIds.get(i);
                        final Integer outputIndex = _queuedOutputs.outputIndexes.get(i);

                        treeMap.put(new TransactionOutputId(transactionId, outputIndex), i);
                    }

                    for (final TransactionOutputId transactionOutputId : treeMap.keySet()) {
                        final int index = treeMap.get(transactionOutputId);
                        queuedOutputs.transactionIds.add(_queuedOutputs.transactionIds.get(index));
                        queuedOutputs.outputIndexes.add(_queuedOutputs.outputIndexes.get(index));
                        queuedOutputs.amounts.add(_queuedOutputs.amounts.get(index));
                        queuedOutputs.scriptTypes.add(_queuedOutputs.scriptTypes.get(index));
                        queuedOutputs.addresses.add(_queuedOutputs.addresses.get(index));
                        queuedOutputs.scriptHashes.add(_queuedOutputs.scriptHashes.get(index));
                        queuedOutputs.slpTransactionIds.add(_queuedOutputs.slpTransactionIds.get(index));
                        queuedOutputs.memoActionTypes.add(_queuedOutputs.memoActionTypes.get(index));
                        queuedOutputs.memoActionIdentifiers.add(_queuedOutputs.memoActionIdentifiers.get(index));
                    }
                }

                blockchainIndexerDatabaseManager.indexTransactionOutputs(queuedOutputs.transactionIds, queuedOutputs.outputIndexes, queuedOutputs.amounts, queuedOutputs.scriptTypes, queuedOutputs.addresses, queuedOutputs.scriptHashes, queuedOutputs.slpTransactionIds, queuedOutputs.memoActionTypes, queuedOutputs.memoActionIdentifiers);
                nanoTimer.stop();
                _indexTransactionOutputMs += nanoTimer.getMillisecondsElapsed();
            }

            {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();

                final QueuedInputs queuedInputs = new QueuedInputs();
                { // Sort the items...
                    final int itemCount = _queuedInputs.transactionIds.getCount();
                    final TreeMap<TransactionOutputId, Integer> treeMap = new TreeMap<>();
                    for (int i = 0; i < itemCount; ++i) {
                        final TransactionId transactionId = _queuedInputs.transactionIds.get(i);
                        final Integer inputIndex = _queuedInputs.inputIndexes.get(i);

                        treeMap.put(new TransactionOutputId(transactionId, inputIndex), i);
                    }

                    for (final TransactionOutputId transactionOutputId : treeMap.keySet()) {
                        final int index = treeMap.get(transactionOutputId);
                        queuedInputs.transactionIds.add(_queuedInputs.transactionIds.get(index));
                        queuedInputs.inputIndexes.add(_queuedInputs.inputIndexes.get(index));
                        queuedInputs.transactionOutputIds.add(_queuedInputs.transactionOutputIds.get(index));
                    }
                }

                blockchainIndexerDatabaseManager.indexTransactionInputs(queuedInputs.transactionIds, queuedInputs.inputIndexes, queuedInputs.transactionOutputIds);
                nanoTimer.stop();
                _indexTransactionInputMs += nanoTimer.getMillisecondsElapsed();
            }

            Logger.trace("_getUnprocessedTransactionsMs=" + _getUnprocessedTransactionsMs + "ms, _dequeueTransactionsForProcessingMs=" + _dequeueTransactionsForProcessingMs + "ms, _getTransactionIdMs=" + _getTransactionIdMs + "ms, _getTransactionMs=" + _getTransactionMs + "ms, _indexTransactionOutputMs=" + _indexTransactionOutputMs + "ms, _indexTransactionInputMs=" + _indexTransactionInputMs + "ms");

            return _greatestProcessedTransactionId;
        }
        catch (final DatabaseException databaseException) {
            throw new ContextException(databaseException);
        }
        finally {
            _clear();
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
    public void markTransactionProcessed(final TransactionId transactionId) throws ContextException {
        if (transactionId == null) { return; }

        if ( (_greatestProcessedTransactionId == null) || (_greatestProcessedTransactionId.longValue() < transactionId.longValue()) ) {
            _greatestProcessedTransactionId = transactionId;
        }
    }

    @Override
    public void storeTransactions(final List<Sha256Hash> transactionHashes, final List<Integer> byteCounts) throws ContextException {
        final int transactionCount = transactionHashes.getCount();
        if (! Util.areEqual(transactionCount, byteCounts.getCount())) { throw new ContextException("transactionHash / byteCount mismatch."); }

        try {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
            final List<TransactionId> transactionIds = transactionDatabaseManager.storeTransactionHashes(transactionHashes, byteCounts);
            for (int i = 0; i < transactionCount; ++i) {
                final Sha256Hash transactionHash = transactionHashes.get(i);
                final TransactionId transactionId = transactionIds.get(i);
                _indexerCache.cacheTransactionId(_cacheIdentifier, transactionHash, transactionId);
            }
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
    public Map<Sha256Hash, TransactionId> getTransactionIds(final List<Sha256Hash> transactionHashes) throws ContextException {
        final MutableHashMap<Sha256Hash, TransactionId> transactionIds = new MutableHashMap<>();
        final MutableList<Sha256Hash> unknownTransactionHashes = new MutableArrayList<>(transactionHashes.getCount());
        for (final Sha256Hash transactionHash : transactionHashes) {
            final TransactionId cachedTransactionId = _indexerCache.getTransactionId(transactionHash);
            if (cachedTransactionId == null) {
                unknownTransactionHashes.add(transactionHash);
            }
            else {
                transactionIds.put(transactionHash, cachedTransactionId);
            }
        }

        if (! unknownTransactionHashes.isEmpty()) {
            try {
                final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();

                final Map<Sha256Hash, TransactionId> foundTransactionIds = transactionDatabaseManager.getTransactionIds(unknownTransactionHashes);
                for (final Tuple<Sha256Hash, TransactionId> entry : foundTransactionIds) {
                    transactionIds.put(entry.first, entry.second);
                    _indexerCache.cacheTransactionId(_cacheIdentifier, entry.first, entry.second);
                }

                nanoTimer.stop();
                _getTransactionIdMs += nanoTimer.getMillisecondsElapsed();
            }
            catch (final DatabaseException databaseException) {
                throw new ContextException(databaseException);
            }
        }

        return transactionIds;
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) throws ContextException {
        return _getTransaction(transactionId);
    }

    @Override
    public void indexTransactionOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final Address address, final Sha256Hash scriptHash, final TransactionId slpTransactionId, final ByteArray memoActionType, final ByteArray memoActionIdentifier) throws ContextException {
        _queuedOutputs.transactionIds.add(transactionId);
        _queuedOutputs.outputIndexes.add(outputIndex);
        _queuedOutputs.amounts.add(amount);
        _queuedOutputs.scriptTypes.add(scriptType);
        _queuedOutputs.addresses.add(address);
        _queuedOutputs.scriptHashes.add(scriptHash);
        _queuedOutputs.slpTransactionIds.add(slpTransactionId);
        _queuedOutputs.memoActionTypes.add(memoActionType);
        _queuedOutputs.memoActionIdentifiers.add(memoActionIdentifier);
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
        catch (final Exception databaseException) {
            throw new ContextException(databaseException);
        }
    }
}

package com.softwareverde.bitcoin.server.module.node;

import com.google.leveldb.LevelDb;
import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.module.node.indexing.DeflatedIndexedAddress;
import com.softwareverde.bitcoin.server.module.node.indexing.IndexedAddress;
import com.softwareverde.bitcoin.server.module.node.store.KeyValueStore;
import com.softwareverde.bitcoin.server.module.node.utxo.IndexedAddressEntryInflater;
import com.softwareverde.bitcoin.server.module.node.utxo.IndexedSpentOutputsInflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.ShortTransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.WorkerManager;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionIndexer implements AutoCloseable {
    public static final String LAST_TRANSACTION_ID_KEY = "lastTransactionId";

    protected final Blockchain _blockchain;
    protected final KeyValueStore _keyValueStore;
    protected final LevelDb<Sha256Hash, Long> _transactionIdDb;
    protected final LevelDb<Long, IndexedTransaction> _transactionDb;
    protected final LevelDb<Sha256Hash, IndexedAddress> _addressDb;
    protected final LevelDb<ShortTransactionOutputIdentifier, Long> _spentOutputsDb;
    protected final AtomicLong _lastTransactionId = new AtomicLong(0L);

    public TransactionIndexer(final File dataDirectory, final Blockchain blockchain, final KeyValueStore keyValueStore) throws Exception {
        _blockchain = blockchain;
        _keyValueStore = keyValueStore;

        final Long lastTransactionId = Util.parseLong(keyValueStore.getString(TransactionIndexer.LAST_TRANSACTION_ID_KEY), 0L);
        _lastTransactionId.set(lastTransactionId);

        final File transactionIdDbDirectory = new File(dataDirectory, "txid");
        if (! transactionIdDbDirectory.exists()) {
            transactionIdDbDirectory.mkdirs();
        }

        final File transactionDbDirectory = new File(dataDirectory, "tx");
        if (! transactionDbDirectory.exists()) {
            transactionDbDirectory.mkdirs();
        }

        final File addressDbDirectory = new File(dataDirectory, "address");
        if (! addressDbDirectory.exists()) {
            addressDbDirectory.mkdirs();
        }

        final File spendOutputsDbDirectory = new File(dataDirectory, "outputs");
        if (! spendOutputsDbDirectory.exists()) {
            spendOutputsDbDirectory.mkdirs();
        }

        final long cacheByteCount = ByteUtil.Unit.Binary.GIBIBYTES / 4L;
        final long writeBufferByteCount = ByteUtil.Unit.Binary.GIBIBYTES / 2L / 4L;

        _transactionIdDb = new LevelDb<>(transactionIdDbDirectory, new TransactionIdEntryInflater());
        _transactionIdDb.open(); // (cacheByteCount, writeBufferByteCount);

        _transactionDb = new LevelDb<>(transactionDbDirectory, new IndexedTransactionEntryInflater());
        _transactionDb.open(); // (cacheByteCount, writeBufferByteCount);

        _addressDb = new LevelDb<>(addressDbDirectory, new IndexedAddressEntryInflater());
        _addressDb.open(); // cacheByteCount, writeBufferByteCount);

        _spentOutputsDb = new LevelDb<>(spendOutputsDbDirectory, new IndexedSpentOutputsInflater());
        _spentOutputsDb.open(); // cacheByteCount, writeBufferByteCount);

        _commitWorker = new WorkerManager(4, 4);
        _commitWorker.setName("Commit Worker");
        _commitWorker.start();
    }

    protected final WorkerManager _commitWorker;
    protected boolean _requiresCommit = false;
    protected int _indexesSinceCommit = 0;

    static class AccumulatingTimer extends NanoTimer {
        protected double _totalTimeMs = 0D;

        @Override
        public void stop() {
            super.stop();
            _totalTimeMs += this.getMillisecondsElapsed();
        }

        public double getTotalMillisecondsElapsed() {
            return _totalTimeMs;
        }
    }

    public synchronized void indexTransactions(final Block block, final Long blockHeight) throws Exception {
        final AccumulatingTimer transactionIdTimer = new AccumulatingTimer();
        final AccumulatingTimer indexTimer = new AccumulatingTimer();
        final AccumulatingTimer databaseTimer = new AccumulatingTimer();
        final AccumulatingTimer transactionDbTimer = new AccumulatingTimer();
        final AccumulatingTimer outputsTimer = new AccumulatingTimer();
        final AccumulatingTimer inputsTimer = new AccumulatingTimer();
        final AccumulatingTimer addressTimer = new AccumulatingTimer();
        final AccumulatingTimer addressDatabaseTimer = new AccumulatingTimer();

        indexTimer.start();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final List<Transaction> transactions = block.getTransactions();
        final int transactionCount = transactions.getCount();

        long diskOffset = BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT;
        diskOffset += CompactVariableLengthInteger.variableLengthIntegerToBytes(transactionCount).getByteCount();

        final MutableHashMap<Sha256Hash, IndexedAddress> stagedScriptHashes = new MutableHashMap<>();

        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            transactionIdTimer.start();
            final Sha256Hash transactionHash = transaction.getHash();
            Long transactionId = _transactionIdDb.get(transactionHash);
            if (transactionId == null) {
                transactionId = _lastTransactionId.incrementAndGet();
                _transactionIdDb.put(transactionHash, transactionId);
            }
            transactionIdTimer.stop();

            transactionDbTimer.start();
            final int transactionByteCount = transaction.getByteCount();
            final IndexedTransaction indexedTransaction = new IndexedTransaction(transactionHash, blockHeight, diskOffset, transactionByteCount);
            _transactionDb.put(transactionId, indexedTransaction);
            diskOffset += transactionByteCount;
            transactionDbTimer.stop();

            outputsTimer.start();
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final Integer outputIndex = transactionOutput.getIndex();
                final LockingScript lockingScript = transactionOutput.getLockingScript();
                final Sha256Hash scriptHash = ScriptBuilder.computeScriptHash(lockingScript);

                IndexedAddress indexedAddress = stagedScriptHashes.get(scriptHash);
                if (indexedAddress == null) {
                    final Address address = scriptPatternMatcher.extractAddress(lockingScript);
                    indexedAddress = new DeflatedIndexedAddress(address);
                    stagedScriptHashes.put(scriptHash, indexedAddress);
                }

                // Add the TransactionOutputIdentifier to the address's list of received outputs.
                final ShortTransactionOutputIdentifier transactionOutputIdentifier = new ShortTransactionOutputIdentifier(transactionId, outputIndex);
                indexedAddress.addTransactionOutput(transactionOutputIdentifier);
            }
            outputsTimer.stop();

            inputsTimer.start();
            if (! isCoinbase) {
                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                    // Index which Transaction the prevout was spent by.
                    final Sha256Hash prevoutTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                    Long prevoutTransactionId = _transactionIdDb.get(prevoutTransactionHash);
                    if (prevoutTransactionId == null) {
                        prevoutTransactionId = _lastTransactionId.incrementAndGet();
                        _transactionIdDb.put(prevoutTransactionHash, prevoutTransactionId);
                    }
                    final Integer prevoutIndex = transactionInput.getPreviousOutputIndex();
                    final ShortTransactionOutputIdentifier previousOutputIdentifier = new ShortTransactionOutputIdentifier(prevoutTransactionId, prevoutIndex);
                    _spentOutputsDb.put(previousOutputIdentifier, transactionId);
                }
            }
            inputsTimer.stop();

            isCoinbase = false;
        }

        addressTimer.start();
        for (final Sha256Hash scriptHash : stagedScriptHashes.getKeys()) {
            final IndexedAddress stagedIndexedAddress = stagedScriptHashes.get(scriptHash);

            addressDatabaseTimer.start();
            IndexedAddress indexedAddress = _addressDb.get(scriptHash);
            addressDatabaseTimer.stop();
            if (indexedAddress == null) {
                indexedAddress = stagedIndexedAddress;
            }
            else {
                indexedAddress.add(stagedIndexedAddress);
            }
            indexedAddress.cacheBytes();

            addressDatabaseTimer.start();
            _addressDb.put(scriptHash, indexedAddress);
            addressDatabaseTimer.stop();
        }
        addressTimer.stop();

        indexTimer.stop();

        databaseTimer.start();
        if (_indexesSinceCommit > 8) {
            _commitWorker.submitTask(new WorkerManager.UnsafeTask() {
                @Override
                public void run() throws Exception {
                    _addressDb.commit();
                }
            });

            _commitWorker.submitTask(new WorkerManager.UnsafeTask() {
                @Override
                public void run() throws Exception {
                    _spentOutputsDb.commit();
                }
            });

            _commitWorker.submitTask(new WorkerManager.UnsafeTask() {
                @Override
                public void run() throws Exception {
                    _transactionIdDb.commit();
                }
            });

            _commitWorker.submitTask(new WorkerManager.UnsafeTask() {
                @Override
                public void run() throws Exception {
                    _transactionDb.commit();
                }
            });

            _commitWorker.waitForCompletion();

            _requiresCommit = false;
            _indexesSinceCommit = 0;
        }
        else {
            _requiresCommit = true;
            _indexesSinceCommit += 1;
        }
        databaseTimer.stop();

        Logger.debug(blockHeight + " (" + transactionCount + " transactions): " +
            "transactionIdTimer=" + transactionIdTimer.getTotalMillisecondsElapsed() + "ms, " +
            "indexTimer=" + indexTimer.getTotalMillisecondsElapsed() + "ms, " +
            "databaseTimer=" + databaseTimer.getTotalMillisecondsElapsed() + "ms, " +
            "transactionDbTimer=" + transactionDbTimer.getTotalMillisecondsElapsed() + "ms, " +
            "outputsTimer=" + outputsTimer.getTotalMillisecondsElapsed() + "ms, " +
            "inputsTimer=" + inputsTimer.getTotalMillisecondsElapsed() + "ms, " +
            "addressTimer=" + addressTimer.getTotalMillisecondsElapsed() + "ms, " +
            "addressDatabaseTimer=" + addressDatabaseTimer.getTotalMillisecondsElapsed() + "ms"
        );
    }

    public ShortTransactionOutputIdentifier getShortTransactionOutputIdentifier(final TransactionOutputIdentifier transactionOutputIdentifier) throws Exception {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final Long transactionId = _transactionIdDb.get(transactionHash);
        if (transactionId == null) { return null; }

        return new ShortTransactionOutputIdentifier(transactionId, outputIndex);
    }

    public TransactionOutputIdentifier getTransactionOutputIdentifier(final ShortTransactionOutputIdentifier transactionOutputIdentifier) throws Exception {
        final Long transactionId = transactionOutputIdentifier.getTransactionId();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final IndexedTransaction indexedTransaction = _transactionDb.get(transactionId);
        final Sha256Hash transactionHash = indexedTransaction.hash;
        return new TransactionOutputIdentifier(transactionHash, outputIndex);
    }

    public IndexedTransaction getIndexedTransaction(final Sha256Hash transactionHash) throws Exception {
        if (! _transactionIdDb.isOpen()) { return null; }
        if (! _transactionDb.isOpen()) { return null; }

        final Long transactionId = _transactionIdDb.get(transactionHash);
        if (transactionId == null) { return null; }

        return _transactionDb.get(transactionId);
    }

    public IndexedTransaction getIndexedTransaction(final Long transactionId) throws Exception {
        if (! _transactionDb.isOpen()) { return null; }
        if (transactionId == null) { return null; }
        return _transactionDb.get(transactionId);
    }

    public Long getSpendingTransactionId(final ShortTransactionOutputIdentifier transactionOutputIdentifier) throws Exception {
        final Long transactionId = transactionOutputIdentifier.getTransactionId();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
        final ShortTransactionOutputIdentifier shortIdentifier = new ShortTransactionOutputIdentifier(transactionId, outputIndex);

        return _spentOutputsDb.get(shortIdentifier);
    }

    public Sha256Hash getSpendingTransactionHash(final TransactionOutputIdentifier transactionOutputIdentifier) throws Exception {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Long transactionId = _transactionIdDb.get(transactionHash);
        if (transactionId == null) { return null; }

        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
        final ShortTransactionOutputIdentifier shortIdentifier = new ShortTransactionOutputIdentifier(transactionId, outputIndex);

        final Long spendingTransactionId = _spentOutputsDb.get(shortIdentifier);
        if (spendingTransactionId == null) { return null; }

        final IndexedTransaction indexedTransaction = _transactionDb.get(spendingTransactionId);
        return indexedTransaction.hash;
    }

    public Sha256Hash getTransactionHash(final Long transactionId) throws Exception {
        final IndexedTransaction indexedTransaction = _transactionDb.get(transactionId);
        if (indexedTransaction == null) { return null; }

        return indexedTransaction.hash;
    }

    public IndexedAddress getIndexedAddress(final Sha256Hash scriptHash) throws Exception {
        return _addressDb.get(scriptHash);
    }

    @Override
    public synchronized void close() throws Exception {
        if (_requiresCommit) {
            _transactionIdDb.commit();
            _transactionDb.commit();
            _addressDb.commit();
            _spentOutputsDb.commit();

            _requiresCommit = false;
            _indexesSinceCommit = 0;
        }

        _commitWorker.close();

        Logger.debug("Closing AddressDb.");
        _addressDb.close();
        Logger.debug("Closing TransactionDb.");
        _transactionDb.close();
        Logger.debug("Closing TransactionIdDb");
        _transactionIdDb.close();
        Logger.debug("Closing SpentOutputsDb.");
        _spentOutputsDb.close();
        Logger.debug("Storing LastTransactionId");
        _keyValueStore.putString(TransactionIndexer.LAST_TRANSACTION_ID_KEY, "" + _lastTransactionId.get());
    }
}

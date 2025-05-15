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
import com.softwareverde.bitcoin.server.module.node.utxo.MetaAddressEntryInflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.ShortTransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.map.mutable.ConcurrentMutableHashMap;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.WorkerManager;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionIndexer implements AutoCloseable {
    public static final Integer MAX_OUTPUTS_PER_BUCKET = 800;
    public static final String LAST_TRANSACTION_ID_KEY = "lastTransactionId";

    protected static final Comparator<ByteArray> SCRIPT_HASH_BUCKET_COMPARATOR = new Comparator<>() {
        @Override
        public int compare(final ByteArray hash0, final ByteArray hash1) {
            final int byteCount0 = hash0.getByteCount();
            final int byteCount1 = hash1.getByteCount();
            final int byteCount = Math.min(byteCount0, byteCount1);
            for (int i = 0; i < byteCount; ++i) {
                final int b0 = ByteUtil.byteToInteger(hash0.getByte(i));
                final int b1 = ByteUtil.byteToInteger(hash1.getByte(i));

                if (b0 < b1) { return -1; }
                if (b0 > b1) { return 1; }
            }
            return Integer.compare(byteCount0, byteCount1);
        }
    };

    protected final Blockchain _blockchain;
    protected final KeyValueStore _keyValueStore;
    protected final LevelDb<Sha256Hash, Long> _transactionIdDb;
    protected final LevelDb<Long, IndexedTransaction> _transactionDb;
    protected final LevelDb<Sha256Hash, Integer> _metaAddressDb;
    protected final LevelDb<ByteArray, IndexedAddress> _addressDb;
    protected final LevelDb<ShortTransactionOutputIdentifier, Long> _spentOutputsDb;
    protected final AtomicLong _lastTransactionId = new AtomicLong(0L);

    protected final WorkerManager _workerManager;

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

        final File metaAddressDbDirectory = new File(dataDirectory, "address-meta");
        if (! metaAddressDbDirectory.exists()) {
            metaAddressDbDirectory.mkdirs();
        }

        final File spendOutputsDbDirectory = new File(dataDirectory, "outputs");
        if (! spendOutputsDbDirectory.exists()) {
            spendOutputsDbDirectory.mkdirs();
        }

        final long cacheByteCount = ByteUtil.Unit.Binary.MEBIBYTES * 32L;
        final long writeBufferByteCount = ByteUtil.Unit.Binary.MEBIBYTES * 128L;
        final Integer bitsPerKey = null; // disable key bloom filter due to excessive memory usage
        final long maxFileByteCount = ByteUtil.Unit.Binary.MEBIBYTES * 128L;
        final long blockByteCount = ByteUtil.Unit.Binary.KIBIBYTES * 2L; // 4kb is the default

        _transactionIdDb = new LevelDb<>(transactionIdDbDirectory, new TransactionIdEntryInflater());
        _transactionIdDb.open(bitsPerKey, cacheByteCount, writeBufferByteCount, maxFileByteCount, blockByteCount);

        _transactionDb = new LevelDb<>(transactionDbDirectory, new IndexedTransactionEntryInflater());
        _transactionDb.open(bitsPerKey, cacheByteCount, writeBufferByteCount, maxFileByteCount, blockByteCount);

        _addressDb = new LevelDb<>(addressDbDirectory, new IndexedAddressEntryInflater());
        _addressDb.open(bitsPerKey, cacheByteCount, writeBufferByteCount, maxFileByteCount, blockByteCount);

        _metaAddressDb = new LevelDb<>(metaAddressDbDirectory, new MetaAddressEntryInflater());
        _metaAddressDb.open(bitsPerKey, cacheByteCount, writeBufferByteCount, maxFileByteCount, blockByteCount);

        _spentOutputsDb = new LevelDb<>(spendOutputsDbDirectory, new IndexedSpentOutputsInflater());
        _spentOutputsDb.open(bitsPerKey, cacheByteCount, writeBufferByteCount, maxFileByteCount, blockByteCount);

        _workerManager = new WorkerManager(4, 8);
        _workerManager.setName("TransactionIndexerWorker");
        _workerManager.start();
    }

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

        public synchronized void addTime(final double timeMs) {
            _totalTimeMs += timeMs;
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

        transactionIdTimer.start();
        final ConcurrentMutableHashMap<Sha256Hash, Long> existingTransactionIds = new ConcurrentMutableHashMap<>(transactionCount);
        for (final Transaction transaction : transactions) {
            _workerManager.submitTask(new WorkerManager.Task() {
                @Override
                public void run() {
                    final Sha256Hash transactionHash = transaction.getHash();
                    final Long transactionId = _transactionIdDb.get(transactionHash);
                    if (transactionId != null) {
                        existingTransactionIds.put(transactionHash, transactionId);
                    }
                }
            });
        }
        _workerManager.waitForCompletion();
        transactionIdTimer.stop();

        for (final Transaction transaction : transactions) {
            transactionIdTimer.start();
            final Sha256Hash transactionHash = transaction.getHash();
            Long transactionId = existingTransactionIds.get(transactionHash);
            if (transactionId == null) {
                transactionId = _lastTransactionId.incrementAndGet();
                _transactionIdDb.put(transactionHash, transactionId);
                existingTransactionIds.put(transactionHash, transactionId);
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
        }

        inputsTimer.start();
        { // Index which Transaction the prevout was spent by.
            boolean isCoinbase = true;
            for (final Transaction transaction : transactions) {
                if (isCoinbase) {
                    isCoinbase = false;
                    continue;
                }

                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                    final Sha256Hash prevoutTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                    if (existingTransactionIds.containsKey(prevoutTransactionHash)) { continue; }

                    _workerManager.submitTask(new WorkerManager.Task() {
                        @Override
                        public void run() {
                            Long prevoutTransactionId = _transactionIdDb.get(prevoutTransactionHash);
                            if (prevoutTransactionId != null) {
                                existingTransactionIds.put(prevoutTransactionHash, prevoutTransactionId);
                            }
                        }
                    });
                }
            }
        }
        _workerManager.waitForCompletion();
        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            if (isCoinbase) {
                isCoinbase = false;
                continue;
            }

            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final Sha256Hash prevoutTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                Long prevoutTransactionId = existingTransactionIds.get(prevoutTransactionHash);
                if (prevoutTransactionId == null) {
                    prevoutTransactionId = _lastTransactionId.incrementAndGet();
                    _transactionIdDb.put(prevoutTransactionHash, prevoutTransactionId);
                    existingTransactionIds.put(prevoutTransactionHash, prevoutTransactionId);
                }

                final Sha256Hash transactionHash = transaction.getHash();
                final Long transactionId = existingTransactionIds.get(transactionHash);
                final Integer prevoutIndex = transactionInput.getPreviousOutputIndex();
                final ShortTransactionOutputIdentifier previousOutputIdentifier = new ShortTransactionOutputIdentifier(prevoutTransactionId, prevoutIndex);
                _spentOutputsDb.put(previousOutputIdentifier, transactionId);
            }
        }
        inputsTimer.stop();

        addressTimer.start();
        final int scriptHashCount = stagedScriptHashes.getCount();
        final ConcurrentMutableHashMap<ByteArray, IndexedAddress> entries = new ConcurrentMutableHashMap<>(scriptHashCount);
        for (final Sha256Hash scriptHash : stagedScriptHashes.getKeys()) {
            _workerManager.submitTask(new WorkerManager.Task() {
                @Override
                public void run() {
                    final MutableByteArray bucketedScriptHash = new MutableByteArray(Sha256Hash.BYTE_COUNT + 2);
                    bucketedScriptHash.setBytes(0, scriptHash);

                    final Integer bucketIndex = Util.coalesce(_metaAddressDb.get(scriptHash));
                    final byte[] bucketIndexBytes = ByteUtil.shortToBytes(bucketIndex);
                    bucketedScriptHash.setBytes(Sha256Hash.BYTE_COUNT, bucketIndexBytes);

                    final IndexedAddress stagedIndexedAddress = stagedScriptHashes.get(scriptHash);

                    IndexedAddress indexedAddress = _addressDb.get(bucketedScriptHash);
                    if (indexedAddress == null) {
                        indexedAddress = stagedIndexedAddress;
                    }
                    else {
                        indexedAddress.add(stagedIndexedAddress);
                    }
                    indexedAddress.cacheBytes();

                    entries.put(bucketedScriptHash, indexedAddress);

                    if (indexedAddress.getTransactionOutputsCount() >= MAX_OUTPUTS_PER_BUCKET) { // Not an exact limit.
                        _metaAddressDb.put(scriptHash, bucketIndex + 1);
                    }
                }
            });
        }
        _workerManager.waitForCompletion();

        addressDatabaseTimer.start();
        _addressDb.put(entries);
        addressDatabaseTimer.stop();
        addressTimer.stop();

        indexTimer.stop();

        databaseTimer.start();
        if (_indexesSinceCommit > 8) {
            // TODO: Old commit code was here.

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
        final MutableByteArray bucketedScriptHash = new MutableByteArray(Sha256Hash.BYTE_COUNT + 2);
        bucketedScriptHash.setBytes(0, scriptHash);

        final Integer bucketIndex = Util.coalesce(_metaAddressDb.get(scriptHash));

        IndexedAddress mergedIndexedAddress = null;
        for (int i = 0; i <= bucketIndex; ++i) {
            final byte[] bucketIndexBytes = ByteUtil.shortToBytes(bucketIndex);
            bucketedScriptHash.setBytes(Sha256Hash.BYTE_COUNT, bucketIndexBytes);

            final IndexedAddress indexedAddressBucket = _addressDb.get(bucketedScriptHash);
            if (indexedAddressBucket == null) { continue; }

            if (mergedIndexedAddress == null){
                mergedIndexedAddress = indexedAddressBucket;
            }
            else {
                mergedIndexedAddress.add(indexedAddressBucket);
            }
        }
        return mergedIndexedAddress;
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

        _workerManager.shutdown();
        _workerManager.close();

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

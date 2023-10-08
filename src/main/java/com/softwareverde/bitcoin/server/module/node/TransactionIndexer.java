package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
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
import com.softwareverde.btreedb.BucketDb;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionIndexer implements AutoCloseable {
    public static final String LAST_TRANSACTION_ID_KEY = "lastTransactionId";

    protected final Blockchain _blockchain;
    protected final KeyValueStore _keyValueStore;
    protected final BucketDb<Sha256Hash, Long> _transactionIdDb;
    protected final BucketDb<Long, IndexedTransaction> _transactionDb;
    protected final BucketDb<Sha256Hash, IndexedAddress> _addressDb;
    protected final BucketDb<ShortTransactionOutputIdentifier, Long> _spentOutputsDb;
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

        _transactionIdDb = new BucketDb<>(transactionIdDbDirectory, new TransactionIdEntryInflater(), 16, 1024 * 1024, 8, false, false);
        _transactionIdDb.open();

        _transactionDb = new BucketDb<>(transactionDbDirectory, new IndexedTransactionEntryInflater(), 16, 1024 * 1024, 8, false, false);
        _transactionDb.open();

        _addressDb = new BucketDb<>(addressDbDirectory, new IndexedAddressEntryInflater(), 16, 1024 * 1024, 16, false, false);
        _addressDb.open();

        _spentOutputsDb = new BucketDb<>(spendOutputsDbDirectory, new IndexedSpentOutputsInflater(), 17, 1024 * 12, 16, false, false);
        _spentOutputsDb.open();
    }

    public synchronized void indexTransactions(final Block block, final Long blockHeight) throws Exception {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final List<Transaction> transactions = block.getTransactions();
        final int transactionCount = transactions.getCount();

        long diskOffset = BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT;
        diskOffset += CompactVariableLengthInteger.variableLengthIntegerToBytes(transactionCount).getByteCount();

        final MutableHashMap<Sha256Hash, IndexedAddress> stagedScriptHashes = new MutableHashMap<>();

        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            Long transactionId = _transactionIdDb.get(transactionHash);
            if (transactionId == null) {
                transactionId = _lastTransactionId.incrementAndGet();
                _transactionIdDb.put(transactionHash, transactionId);
            }

            final int transactionByteCount = transaction.getByteCount();
            final IndexedTransaction indexedTransaction = new IndexedTransaction(transactionHash, blockHeight, diskOffset, transactionByteCount);
            _transactionDb.put(transactionId, indexedTransaction);
            diskOffset += transactionByteCount;

            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final Integer outputIndex = transactionOutput.getIndex();
                final LockingScript lockingScript = transactionOutput.getLockingScript();
                final Sha256Hash scriptHash = ScriptBuilder.computeScriptHash(lockingScript);

                IndexedAddress indexedAddress = stagedScriptHashes.get(scriptHash);
                if (indexedAddress == null) {
                    final Address address = scriptPatternMatcher.extractAddress(lockingScript);
                    indexedAddress = new IndexedAddress(address);
                    stagedScriptHashes.put(scriptHash, indexedAddress);
                }

                // Add the TransactionOutputIdentifier to the address's list of received outputs.
                final ShortTransactionOutputIdentifier transactionOutputIdentifier = new ShortTransactionOutputIdentifier(transactionId, outputIndex);
                indexedAddress.addTransactionOutput(transactionOutputIdentifier);
            }

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

            isCoinbase = false;
        }

        for (final Sha256Hash scriptHash : stagedScriptHashes.getKeys()) {
            final IndexedAddress stagedIndexedAddress = stagedScriptHashes.get(scriptHash);

            IndexedAddress indexedAddress = _addressDb.get(scriptHash);
            if (indexedAddress == null) {
                indexedAddress = stagedIndexedAddress;
            }
            else {
                indexedAddress.add(stagedIndexedAddress);
            }
            _addressDb.put(scriptHash, indexedAddress);
        }

        _transactionIdDb.commit();
        _transactionDb.commit();
        _addressDb.commit();
        _spentOutputsDb.commit();
    }

    public synchronized ShortTransactionOutputIdentifier getShortTransactionOutputIdentifier(final TransactionOutputIdentifier transactionOutputIdentifier) throws Exception {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final Long transactionId = _transactionIdDb.get(transactionHash);
        if (transactionId == null) { return null; }

        return new ShortTransactionOutputIdentifier(transactionId, outputIndex);
    }

    public synchronized TransactionOutputIdentifier getTransactionOutputIdentifier(final ShortTransactionOutputIdentifier transactionOutputIdentifier) throws Exception {
        final Long transactionId = transactionOutputIdentifier.getTransactionId();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final IndexedTransaction indexedTransaction = _transactionDb.get(transactionId);
        final Sha256Hash transactionHash = indexedTransaction.hash;
        return new TransactionOutputIdentifier(transactionHash, outputIndex);
    }

    public synchronized IndexedTransaction getIndexedTransaction(final Sha256Hash transactionHash) throws Exception {
        if (! _transactionIdDb.isOpen()) { return null; }
        if (! _transactionDb.isOpen()) { return null; }

        final Long transactionId = _transactionIdDb.get(transactionHash);
        if (transactionId == null) { return null; }

        return _transactionDb.get(transactionId);
    }

    public synchronized IndexedTransaction getIndexedTransaction(final Long transactionId) throws Exception {
        if (! _transactionDb.isOpen()) { return null; }
        if (transactionId == null) { return null; }
        return _transactionDb.get(transactionId);
    }

    public synchronized Long getSpendingTransactionId(final ShortTransactionOutputIdentifier transactionOutputIdentifier) throws Exception {
        final Long transactionId = transactionOutputIdentifier.getTransactionId();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
        final ShortTransactionOutputIdentifier shortIdentifier = new ShortTransactionOutputIdentifier(transactionId, outputIndex);

        return _spentOutputsDb.get(shortIdentifier);
    }

    public synchronized Sha256Hash getSpendingTransactionHash(final TransactionOutputIdentifier transactionOutputIdentifier) throws Exception {
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

    public synchronized Sha256Hash getTransactionHash(final Long transactionId) throws Exception {
        final IndexedTransaction indexedTransaction = _transactionDb.get(transactionId);
        if (indexedTransaction == null) { return null; }

        return indexedTransaction.hash;
    }

    public synchronized IndexedAddress getIndexedAddress(final Sha256Hash scriptHash) throws Exception {
        return _addressDb.get(scriptHash);
    }

    @Override
    public synchronized void close() throws Exception {
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

package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.module.node.utxo.IndexedAddressEntryInflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.btreedb.BucketDb;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.io.File;

public class TransactionIndexer implements AutoCloseable {
    protected final Blockchain _blockchain;
    protected final BucketDb<Sha256Hash, IndexedTransaction> _transactionDb;
    protected final BucketDb<Sha256Hash, IndexedAddress> _addressDb;

    public TransactionIndexer(final File dataDirectory, final Blockchain blockchain) throws Exception {
        _blockchain = blockchain;

        final File transactionDbDirectory = new File(dataDirectory, "tx");
        if (! transactionDbDirectory.exists()) {
            transactionDbDirectory.mkdirs();
        }

        final File addressDbDirectory = new File(dataDirectory, "address");
        if (! addressDbDirectory.exists()) {
            addressDbDirectory.mkdirs();
        }

        _transactionDb = new BucketDb<>(transactionDbDirectory, new IndexedTransactionEntryInflater());
        _transactionDb.open();

        _addressDb = new BucketDb<>(addressDbDirectory, new IndexedAddressEntryInflater());
        _addressDb.open();
    }

    public void indexTransactions(final Block block, final Long blockHeight) throws Exception {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final List<Transaction> transactions = block.getTransactions();
        final int transactionCount = transactions.getCount();
        // _transactionDb.resizeCapacity(transactionCount, _falsePositiveRate);

        long diskOffset = BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT;
        diskOffset += CompactVariableLengthInteger.variableLengthIntegerToBytes(transactionCount).getByteCount();

        final MutableHashMap<Sha256Hash, IndexedAddress> stagedScriptHashes = new MutableHashMap<>();

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final int transactionByteCount = transaction.getByteCount();
            final IndexedTransaction indexedTransaction = new IndexedTransaction(blockHeight, diskOffset, transactionByteCount);
            _transactionDb.put(transactionHash, indexedTransaction);
            diskOffset += transactionByteCount;

            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final LockingScript lockingScript = transactionOutput.getLockingScript();
                final Sha256Hash scriptHash = ScriptBuilder.computeScriptHash(lockingScript);

                IndexedAddress indexedAddress = stagedScriptHashes.get(scriptHash);
                if (indexedAddress == null) {
                    final Address address = scriptPatternMatcher.extractAddress(lockingScript);
                    indexedAddress = new IndexedAddress(address);
                    stagedScriptHashes.put(scriptHash, indexedAddress);
                }

                indexedAddress.addTransactionOutput(transactionHash, transactionOutput);

                // Do addresses really need the full output in the IndexedAddress?  I think they just need the output identifier and can grab the rest from disk.
            }

            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                final IndexedTransaction previousIndexedTransaction = _transactionDb.get(previousTransactionHash);
                if (previousIndexedTransaction == null) { continue; }
                final Transaction previousTransaction = _blockchain.getTransaction(previousIndexedTransaction);
                if (previousTransaction == null) { continue; }

                // Get the TransactionInput's prevout's Address and attach the TransactionInput to that Address's sentInputs.

                final Integer outputIndex = transactionInput.getPreviousOutputIndex();
                final TransactionOutput transactionOutput = previousTransaction.getTransactionOutput(outputIndex);
                if (transactionOutput == null) { continue; }

                final LockingScript lockingScript = transactionOutput.getLockingScript();
                final Sha256Hash scriptHash = ScriptBuilder.computeScriptHash(lockingScript);

                IndexedAddress indexedAddress = stagedScriptHashes.get(scriptHash);
                if (indexedAddress == null) {
                    final Address address = scriptPatternMatcher.extractAddress(lockingScript);
                    indexedAddress = new IndexedAddress(address);
                    stagedScriptHashes.put(scriptHash, indexedAddress);
                }

                indexedAddress.addTransactionInput(transactionHash, transactionInput);
            }
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

        _transactionDb.commit();
        _addressDb.commit();
    }

    public IndexedTransaction getIndexedTransaction(final Sha256Hash transactionHash) throws Exception {
        return _transactionDb.get(transactionHash);
    }

    @Override
    public void close() throws Exception {
        _addressDb.close();
        _transactionDb.close();
    }
}

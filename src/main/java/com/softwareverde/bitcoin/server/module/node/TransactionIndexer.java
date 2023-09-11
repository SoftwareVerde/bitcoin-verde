package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputEntryInflater;
import com.softwareverde.bitcoin.server.module.node.utxo.IndexedAddressEntryInflater;
import com.softwareverde.bitcoin.server.module.node.utxo.IndexedSpentOutputsInflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
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

import java.io.File;

public class TransactionIndexer implements AutoCloseable {
    protected final Blockchain _blockchain;
    protected final BucketDb<Sha256Hash, IndexedTransaction> _transactionDb;
    protected final BucketDb<Sha256Hash, IndexedAddress> _addressDb;
    protected final BucketDb<TransactionOutputIdentifier, Sha256Hash> _spentOutputsDb;

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

        final File spendOutputsDbDirectory = new File(dataDirectory, "outputs");
        if (! spendOutputsDbDirectory.exists()) {
            spendOutputsDbDirectory.mkdirs();
        }

        _transactionDb = new BucketDb<>(transactionDbDirectory, new IndexedTransactionEntryInflater(), 16, 1024 * 1024, 8, true);
        _transactionDb.open();

        _addressDb = new BucketDb<>(addressDbDirectory, new IndexedAddressEntryInflater(), 16, 1024 * 1024, 16, false);
        _addressDb.open();

        _spentOutputsDb = new BucketDb<>(spendOutputsDbDirectory, new IndexedSpentOutputsInflater(), 17, 1024 * 12, 16, true);
        _spentOutputsDb.open();
    }

    public synchronized void indexTransactions(final Block block, final Long blockHeight) throws Exception {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final List<Transaction> transactions = block.getTransactions();
        final int transactionCount = transactions.getCount();

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
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                indexedAddress.addTransactionOutput(transactionOutputIdentifier);
            }

            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                // Index which Transaction the prevout was spent by.
                final TransactionOutputIdentifier previousOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                _spentOutputsDb.put(previousOutputIdentifier, transactionHash);
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
        _spentOutputsDb.commit();
    }

    public IndexedTransaction getIndexedTransaction(final Sha256Hash transactionHash) throws Exception {
        if (! _transactionDb.isOpen()) { return null; }
        return _transactionDb.get(transactionHash);
    }

    public Sha256Hash getSpendingTransactionHash(final TransactionOutputIdentifier transactionOutputIdentifier) throws Exception {
        return _spentOutputsDb.get(transactionOutputIdentifier);
    }

    public IndexedAddress getIndexedAddress(final Sha256Hash scriptHash) throws Exception {
        return _addressDb.get(scriptHash);
    }

    @Override
    public synchronized void close() throws Exception {
        Logger.debug("Closing AddressDb.");
        _addressDb.close();
        Logger.debug("Closing TransactionDb.");
        _transactionDb.close();
        Logger.debug("Closing SpentOutputsDb.");
        _spentOutputsDb.close();
    }
}

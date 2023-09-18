package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.address.TypedAddress;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.ShortTransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.set.Set;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.util.Comparator;

public class BlockchainQueryAddressHandler implements NodeRpcHandler.QueryAddressHandler {
    protected final Blockchain _blockchain;
    protected final TransactionIndexer _transactionIndexer;
    protected final TransactionMempool _mempool;

    public BlockchainQueryAddressHandler(final Blockchain blockchain, final TransactionIndexer transactionIndexer, final TransactionMempool transactionMempool) {
        _blockchain = blockchain;
        _transactionIndexer = transactionIndexer;
        _mempool = transactionMempool;
    }

    @Override
    public Long getBalance(final TypedAddress address, final Boolean includeUnconfirmedTransactions) {
        final Sha256Hash scriptHash = ScriptBuilder.computeScriptHash(address);
        return this.getBalance(scriptHash, includeUnconfirmedTransactions);
    }

    @Override
    public Long getBalance(final Sha256Hash scriptHash, final Boolean includeUnconfirmedTransactions) {
        try {
            final IndexedAddress indexedAddress = _transactionIndexer.getIndexedAddress(scriptHash);
            if (indexedAddress == null) { return 0L; }

            final List<ShortTransactionOutputIdentifier> receivedOutputs = indexedAddress.getTransactionOutputs();

            long totalAmount = 0L;
            final MutableHashMap<Sha256Hash, Transaction> cachedTransactions = new MutableHashMap<>();
            for (final ShortTransactionOutputIdentifier transactionOutputIdentifier : receivedOutputs) {
                final Long spendingTransactionId = _transactionIndexer.getSpendingTransactionId(transactionOutputIdentifier);
                if (spendingTransactionId == null) { // Output is unspent.
                    final Long transactionId = transactionOutputIdentifier.getTransactionId();
                    final Sha256Hash transactionHash = _transactionIndexer.getTransactionHash(transactionId);
                    final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

                    final Transaction transaction;
                    {
                        if (cachedTransactions.containsKey(transactionHash)) {
                            transaction = cachedTransactions.get(transactionHash);
                        }
                        else {
                            final IndexedTransaction indexedTransaction = _transactionIndexer.getIndexedTransaction(transactionHash);
                            transaction = _blockchain.getTransaction(indexedTransaction);
                            cachedTransactions.put(transactionHash, transaction);
                        }
                    }

                    final TransactionOutput transactionOutput = transaction.getTransactionOutput(outputIndex);
                    totalAmount += transactionOutput.getAmount();
                }
            }

            if (includeUnconfirmedTransactions) {
                final Set<ShortTransactionOutputIdentifier> receivedOutputsSet = new MutableHashSet<>(receivedOutputs);
                for (final TransactionWithFee mempoolTransaction : _mempool.getTransactions()) {
                    for (final TransactionInput transactionInput : mempoolTransaction.transaction.getTransactionInputs()) {
                        final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                        final ShortTransactionOutputIdentifier shortIdentifier = _transactionIndexer.getShortTransactionOutputIdentifier(transactionOutputIdentifier);
                        if (receivedOutputsSet.contains(shortIdentifier)) {
                            final Transaction transaction;
                            {
                                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                                if (cachedTransactions.containsKey(transactionHash)) {
                                    transaction = cachedTransactions.get(transactionHash);
                                }
                                else {
                                    final TransactionWithFee mempoolUtxoTransaction = _mempool.getTransaction(transactionHash);
                                    if (mempoolUtxoTransaction != null) {
                                        transaction = mempoolUtxoTransaction.transaction;
                                    }
                                    else {
                                        final IndexedTransaction indexedTransaction = _transactionIndexer.getIndexedTransaction(transactionHash);
                                        transaction = _blockchain.getTransaction(indexedTransaction);
                                    }
                                    cachedTransactions.put(transactionHash, transaction);
                                }
                            }

                            final TransactionOutput transactionOutput = transaction.getTransactionOutput(transactionOutputIdentifier.getOutputIndex());
                            totalAmount -= transactionOutput.getAmount();
                        }
                    }

                    for (final TransactionOutput mempoolTransactionOutput  : mempoolTransaction.transaction.getTransactionOutputs()) {
                        final Sha256Hash mempoolScriptHash = ScriptBuilder.computeScriptHash(mempoolTransactionOutput.getLockingScript());
                        if (Util.areEqual(scriptHash, mempoolScriptHash)) {
                            totalAmount += mempoolTransactionOutput.getAmount();
                        }
                    }
                }
            }

            return totalAmount;
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getAddressTransactions(final TypedAddress address) {
        final Sha256Hash scriptHash = ScriptBuilder.computeScriptHash(address);
        return this.getAddressTransactions(scriptHash);
    }

    @Override
    public List<Transaction> getAddressTransactions(final Sha256Hash scriptHash) {
        try {
            final IndexedAddress indexedAddress = _transactionIndexer.getIndexedAddress(scriptHash);
            if (indexedAddress == null) { return new MutableArrayList<>(0); }

            final List<ShortTransactionOutputIdentifier> receivedOutputs = indexedAddress.getTransactionOutputs();

            final MutableHashMap<Sha256Hash, Long> transactionBlockHeights = new MutableHashMap<>(); // TransactionId -> BlockHeight
            final MutableHashMap<Sha256Hash, Transaction> cachedTransactions = new MutableHashMap<>();
            for (final ShortTransactionOutputIdentifier transactionOutputIdentifier : receivedOutputs) {
                final Long transactionId = transactionOutputIdentifier.getTransactionId();
                final Sha256Hash transactionHash = _transactionIndexer.getTransactionHash(transactionId);
                if (! cachedTransactions.containsKey(transactionHash)) {
                    final IndexedTransaction indexedTransaction = _transactionIndexer.getIndexedTransaction(transactionId);
                    final Transaction transaction = _blockchain.getTransaction(indexedTransaction);
                    cachedTransactions.put(transactionHash, transaction);
                    transactionBlockHeights.put(transactionHash, indexedTransaction.blockHeight);
                }

                final Long spendingTransactionId = _transactionIndexer.getSpendingTransactionId(transactionOutputIdentifier);
                if (spendingTransactionId != null) {
                    final Sha256Hash spendingTransactionHash = _transactionIndexer.getTransactionHash(spendingTransactionId);
                    if (! cachedTransactions.containsKey(spendingTransactionHash)) {
                        final IndexedTransaction indexedTransaction = _transactionIndexer.getIndexedTransaction(spendingTransactionId);
                        final Transaction transaction = _blockchain.getTransaction(indexedTransaction);
                        cachedTransactions.put(spendingTransactionHash, transaction);
                        transactionBlockHeights.put(spendingTransactionHash, indexedTransaction.blockHeight);
                    }
                }
            }

            final Long blockHeight = (_blockchain.getHeadBlockHeaderHeight() + 1L);
            for (final TransactionWithFee mempoolTransactionWithFee : _mempool.getTransactions()) {
                final Transaction mempoolTransaction = mempoolTransactionWithFee.transaction;
                final Sha256Hash mempoolTransactionHash = mempoolTransaction.getHash();

                for (final TransactionInput transactionInput : mempoolTransaction.getTransactionInputs()) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final ShortTransactionOutputIdentifier shortIdentifier = _transactionIndexer.getShortTransactionOutputIdentifier(transactionOutputIdentifier);
                    if (receivedOutputs.contains(shortIdentifier)) {
                        cachedTransactions.put(mempoolTransactionHash, mempoolTransaction);
                        transactionBlockHeights.put(mempoolTransactionHash, blockHeight);
                    }
                }

                for (final TransactionOutput mempoolTransactionOutput : mempoolTransaction.getTransactionOutputs()) {
                    final Sha256Hash mempoolScriptHash = ScriptBuilder.computeScriptHash(mempoolTransactionOutput.getLockingScript());
                    if (Util.areEqual(scriptHash, mempoolScriptHash)) {
                        cachedTransactions.put(mempoolTransactionHash, mempoolTransaction);
                        transactionBlockHeights.put(mempoolTransactionHash, blockHeight);
                    }
                }
            }

            final MutableList<Transaction> transactions = new MutableArrayList<>(cachedTransactions.getValues());
            transactions.sort(new Comparator<>() {
                @Override
                public int compare(final Transaction transaction0, final Transaction transaction1) {
                    final Sha256Hash transactionHash0 = transaction0.getHash();
                    final Sha256Hash transactionHash1 = transaction1.getHash();

                    final Long blockHeight0 = transactionBlockHeights.get(transactionHash0);
                    final Long blockHeight1 = transactionBlockHeights.get(transactionHash1);

                    // Handle null...
                    if ( (blockHeight0 == null) && (blockHeight1 == null) ) { return 0; }
                    if (blockHeight0 == null) { return 1; }
                    if (blockHeight1 == null) { return -1; }

                    return Long.compare(blockHeight0, blockHeight1);
                }
            });

            return transactions;
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public List<Sha256Hash> getAddressTransactionHashes(final TypedAddress address) {
        final Sha256Hash scriptHash = ScriptBuilder.computeScriptHash(address);
        return this.getAddressTransactionHashes(scriptHash);
    }

    @Override
    public List<Sha256Hash> getAddressTransactionHashes(final Sha256Hash scriptHash) {
        try {
            final IndexedAddress indexedAddress = _transactionIndexer.getIndexedAddress(scriptHash);
            final List<ShortTransactionOutputIdentifier> receivedOutputs = indexedAddress.getTransactionOutputs();

            final MutableHashMap<Sha256Hash, Long> transactionBlockHeights = new MutableHashMap<>();
            for (final ShortTransactionOutputIdentifier transactionOutputIdentifier : receivedOutputs) {
                final Long transactionId = transactionOutputIdentifier.getTransactionId();
                final Sha256Hash transactionHash = _transactionIndexer.getTransactionHash(transactionId);
                if (! transactionBlockHeights.containsKey(transactionHash)) {
                    final IndexedTransaction indexedTransaction = _transactionIndexer.getIndexedTransaction(transactionHash);
                    transactionBlockHeights.put(transactionHash, indexedTransaction.blockHeight);
                }

                final Long spendingTransactionId = _transactionIndexer.getSpendingTransactionId(transactionOutputIdentifier);
                if (spendingTransactionId != null) {
                    final Sha256Hash spendingTransactionHash = _transactionIndexer.getTransactionHash(spendingTransactionId);
                    if (! transactionBlockHeights.containsKey(spendingTransactionHash)) {
                        final IndexedTransaction indexedTransaction = _transactionIndexer.getIndexedTransaction(spendingTransactionHash);
                        transactionBlockHeights.put(spendingTransactionHash, indexedTransaction.blockHeight);
                    }
                }
            }

            final Long blockHeight = (_blockchain.getHeadBlockHeaderHeight() + 1L);
            for (final TransactionWithFee mempoolTransactionWithFee : _mempool.getTransactions()) {
                final Transaction mempoolTransaction = mempoolTransactionWithFee.transaction;
                final Sha256Hash mempoolTransactionHash = mempoolTransaction.getHash();

                for (final TransactionInput transactionInput : mempoolTransaction.getTransactionInputs()) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final ShortTransactionOutputIdentifier shotIdentifier = _transactionIndexer.getShortTransactionOutputIdentifier(transactionOutputIdentifier);
                    if (receivedOutputs.contains(shotIdentifier)) {
                        transactionBlockHeights.put(mempoolTransactionHash, blockHeight);
                    }
                }

                for (final TransactionOutput mempoolTransactionOutput : mempoolTransaction.getTransactionOutputs()) {
                    final Sha256Hash mempoolScriptHash = ScriptBuilder.computeScriptHash(mempoolTransactionOutput.getLockingScript());
                    if (Util.areEqual(scriptHash, mempoolScriptHash)) {
                        transactionBlockHeights.put(mempoolTransactionHash, blockHeight);
                    }
                }
            }

            final MutableList<Sha256Hash> transactionHashes = new MutableArrayList<>(transactionBlockHeights.getKeys());
            transactionHashes.sort(new Comparator<>() {
                @Override
                public int compare(final Sha256Hash transactionHash0, final Sha256Hash transactionHash1) {
                    final Long blockHeight0 = transactionBlockHeights.get(transactionHash0);
                    final Long blockHeight1 = transactionBlockHeights.get(transactionHash1);

                    // Handle null...
                    if ( (blockHeight0 == null) && (blockHeight1 == null) ) { return 0; }
                    if (blockHeight0 == null) { return 1; }
                    if (blockHeight1 == null) { return -1; }

                    return Long.compare(blockHeight0, blockHeight1);
                }
            });

            return transactionHashes;
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }
}

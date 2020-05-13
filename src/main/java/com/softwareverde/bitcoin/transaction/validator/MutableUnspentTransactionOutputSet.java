package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.JavaListWrapper;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class MutableUnspentTransactionOutputSet implements UnspentTransactionOutputSet {
    protected final HashMap<TransactionOutputIdentifier, TransactionOutput> _transactionOutputs = new HashMap<TransactionOutputIdentifier, TransactionOutput>();
    protected final HashMap<Sha256Hash, Long> _transactionBlockHeights = new HashMap<Sha256Hash, Long>();
    protected final HashMap<Long, Sha256Hash> _coinbaseTransactionHashesByBlockHeight = new HashMap<Long, Sha256Hash>();
    protected final HashMap<Long, Sha256Hash> _blockHashesByBlockHeight = new HashMap<Long, Sha256Hash>();

    public MutableUnspentTransactionOutputSet() { }

    /**
     * Loads all outputs spent by the provided block.
     *  Returns true if all of the outputs were found, and false if at least one output could not be found.
     *  Outputs may not be found in the case of an invalid block, but also if its predecessor has not been validated yet.
     */
    public synchronized Boolean loadOutputsForBlock(final FullNodeDatabaseManager databaseManager, final Block block, final Long blockHeight) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

        final Sha256Hash blockHash = block.getHash();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);

        _blockHashesByBlockHeight.put(blockHeight, blockHash);

        final List<Transaction> transactions = block.getTransactions();

        final HashSet<Sha256Hash> transactionsWithUnknownBlockHeights = new HashSet<Sha256Hash>();

        for (final Transaction transaction : transactions) { // NOTE: adding the TransactionBlockHeights is done in its own loop so unknownTransactionBlockHeights may exclude this Block's Transactions in a single pass...
            final Sha256Hash transactionHash = transaction.getHash();
            _transactionBlockHeights.put(transactionHash, blockHeight);
        }

        Transaction coinbaseTransaction = null;
        final HashSet<TransactionOutputIdentifier> requiredTransactionOutputs = new HashSet<TransactionOutputIdentifier>();
        final HashSet<TransactionOutputIdentifier> newOutputs = new HashSet<TransactionOutputIdentifier>();
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            _transactionBlockHeights.put(transactionHash, blockHeight);

            if (coinbaseTransaction == null) { // Skip the coinbase transaction...
                coinbaseTransaction = transaction;
                continue;
            }

            { // Add the PreviousTransactionOutputs to the list of outputs to retrieve...
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final Sha256Hash previousTransactionHash = transactionOutputIdentifier.getTransactionHash();

                    if (! _transactionBlockHeights.containsKey(previousTransactionHash)) {
                        transactionsWithUnknownBlockHeights.add(previousTransactionHash);
                    }

                    final boolean isUnique = requiredTransactionOutputs.add(transactionOutputIdentifier);
                    if (! isUnique) { // Two inputs cannot spent the same output...
                        final boolean isAllowedDuplicate = ALLOWED_DUPLICATE_TRANSACTION_HASHES.contains(previousTransactionHash);
                        if (! isAllowedDuplicate) {
                            return false;
                        }
                    }
                }
            }

            { // Catalogue the new outputs that are created in this block to prevent loading them from disk...
                final List<TransactionOutputIdentifier> outputIdentifiers = TransactionOutputIdentifier.fromTransactionOutputs(transaction);
                for (final TransactionOutputIdentifier transactionOutputIdentifier : outputIdentifiers) {
                    newOutputs.add(transactionOutputIdentifier);
                }
            }
        }

        if (coinbaseTransaction != null) {
            final Sha256Hash coinbaseTransactionHash = coinbaseTransaction.getHash();
            _coinbaseTransactionHashesByBlockHeight.put(blockHeight, coinbaseTransactionHash);
        }

        requiredTransactionOutputs.removeAll(newOutputs); // New outputs created by this block are not added to this UTXO set.

        { // Load the BlockHeights for the unknown Transactions (the previous Transactions being spent by (and outside of) this block)...
            final Map<Sha256Hash, BlockId> transactionBlockIds = transactionDatabaseManager.getBlockIds(blockchainSegmentId, JavaListWrapper.wrap(transactionsWithUnknownBlockHeights));
            final HashSet<BlockId> uniqueBlockIds = new HashSet<BlockId>(transactionBlockIds.values());
            final Map<BlockId, Long> blockHeights = blockHeaderDatabaseManager.getBlockHeights(JavaListWrapper.wrap(uniqueBlockIds));
            for (final Sha256Hash transactionHash : transactionBlockIds.keySet()) {
                final BlockId transactionBlockId = transactionBlockIds.get(transactionHash);
                final Long transactionBlockHeight = blockHeights.get(transactionBlockId);
                _transactionBlockHeights.put(transactionHash, transactionBlockHeight);
            }
        }

        boolean allTransactionOutputsWereLoaded = true;
        final List<TransactionOutputIdentifier> transactionOutputIdentifiers = new MutableList<TransactionOutputIdentifier>(requiredTransactionOutputs);
        final List<TransactionOutput> transactionOutputs = transactionDatabaseManager.getUnspentTransactionOutputs(transactionOutputIdentifiers);
        for (int i = 0; i < transactionOutputs.getCount(); ++i) {
            final TransactionOutputIdentifier transactionOutputIdentifier = transactionOutputIdentifiers.get(i);
            final TransactionOutput transactionOutput = transactionOutputs.get(i);
            if (transactionOutput == null) {
                if (allTransactionOutputsWereLoaded) {
                    Logger.debug("Missing UTXO: " + transactionOutputIdentifier);
                }
                allTransactionOutputsWereLoaded = false;
                continue; // Continue processing for pre-loading the UTXO set for pending blocks...
            }

            _transactionOutputs.put(transactionOutputIdentifier, transactionOutput);
        }

        return allTransactionOutputsWereLoaded;
    }

    @Override
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _transactionOutputs.get(transactionOutputIdentifier);
    }

    @Override
    public Long getBlockHeight(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        return _transactionBlockHeights.get(transactionHash);
    }

    @Override
    public Sha256Hash getBlockHash(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Long blockHeight = _transactionBlockHeights.get(transactionHash);
        if (blockHeight == null) { return null; }
        return _blockHashesByBlockHeight.get(blockHeight);
    }

    @Override
    public Boolean isCoinbaseTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Long blockHeight = _transactionBlockHeights.get(transactionHash);
        if (blockHeight == null) { return null; }

        final Sha256Hash blockCoinbaseTransactionHash = _coinbaseTransactionHashesByBlockHeight.get(blockHeight);
        if (blockCoinbaseTransactionHash == null) { return null; }

        return Util.areEqual(blockCoinbaseTransactionHash, transactionHash);
    }

    /**
     * Adds new outputs created by the provided block and removes outputs spent by the block.
     */
    public synchronized void update(Block block, final Long blockHeight) {
        final List<Transaction> transactions = block.getTransactions();

        final Sha256Hash blockHash = block.getHash();
        _blockHashesByBlockHeight.put(blockHeight, blockHash);

        Transaction coinbaseTransaction = null;
        { // Add the new outputs created by the block...
            for (final Transaction transaction : transactions) {
                if (coinbaseTransaction == null) {
                    coinbaseTransaction = transaction;
                }

                final Sha256Hash transactionHash = transaction.getHash();
                _transactionBlockHeights.put(transactionHash, blockHeight);

                int outputIndex = 0;
                final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                for (final TransactionOutput transactionOutput : transactionOutputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                    _transactionOutputs.put(transactionOutputIdentifier, transactionOutput);
                    outputIndex += 1;
                }
            }
        }

        if (coinbaseTransaction != null) {
            final Sha256Hash coinbaseTransactionHash = coinbaseTransaction.getHash();
            _coinbaseTransactionHashesByBlockHeight.put(blockHeight, coinbaseTransactionHash);
        }

        { // Remove the spent PreviousTransactionOutputs from the list of outputs to retrieve...
            for (final Transaction transaction : transactions) {
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    _transactionOutputs.remove(transactionOutputIdentifier);
                }
            }
        }
    }

    public synchronized void clear() {
        _transactionOutputs.clear();
        _transactionBlockHeights.clear();
        _coinbaseTransactionHashesByBlockHeight.clear();
    }
}

package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.ImmutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MultiTimer;

public class MutableUnspentTransactionOutputSet implements UnspentTransactionOutputContext {
    protected final MutableArrayList<TransactionOutputIdentifier> _missingOutputIdentifiers = new MutableArrayList<>();
    protected final MutableHashMap<TransactionOutputIdentifier, UnspentTransactionOutput> _transactionOutputs = new MutableHashMap<>();
    protected final MutableHashMap<Sha256Hash, Long> _transactionBlockHeights = new MutableHashMap<>();
    protected final MutableHashSet<TransactionOutputIdentifier> _preActivationTokenForgeries = new MutableHashSet<>();

    public MutableUnspentTransactionOutputSet() { }

    /**
     * Loads all outputs spent by the provided block.
     *  Returns true if all of the outputs were found, and false if at least one output could not be found.
     *  Outputs may not be found in the case of an invalid block, but also if its predecessor has not been validated yet.
     *  The BlockHeader for the provided Block must have been stored before attempting to load its outputs.
     */
    public synchronized Boolean quicklyLoadOutputsForBlock(final Blockchain blockchain, final Block block, final Long blockHeight, final UpgradeSchedule upgradeSchedule) throws DatabaseException {
        final MultiTimer multiTimer = new MultiTimer();

        final MutableHashSet<TransactionOutputIdentifier> requiredTransactionOutputs = _stepOne(block, blockHeight, multiTimer);
        if (requiredTransactionOutputs == null) { return false; }

        final List<TransactionOutputIdentifier> transactionOutputIdentifiers = new MutableArrayList<>(requiredTransactionOutputs);
        final List<TransactionOutputIdentifier> missingOutputIdentifiers = _stepTwo(transactionOutputIdentifiers, blockchain, blockHeight, upgradeSchedule, multiTimer);
        _missingOutputIdentifiers.addAll(missingOutputIdentifiers);

        return missingOutputIdentifiers.isEmpty();
    }

    protected final MutableHashSet<TransactionOutputIdentifier> _stepOne(final Block block, final Long blockHeight, final MultiTimer multiTimer) throws DatabaseException {
        // final MultiTimer multiTimer = new MultiTimer();
        multiTimer.start();

        final List<Transaction> transactions = block.getTransactions();

        Transaction coinbaseTransaction = null;
        final MutableHashSet<TransactionOutputIdentifier> requiredTransactionOutputs = new MutableHashSet<>();
        final MutableHashSet<TransactionOutputIdentifier> newOutputs = new MutableHashSet<>();
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            _transactionBlockHeights.put(transactionHash, blockHeight);

            if (coinbaseTransaction == null) { // Skip the coinbase transaction...
                coinbaseTransaction = transaction;
                continue;
            }

            { // Add the PreviousTransactionOutputs to the list of outputs to retrieve and check for duplicates...
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final Sha256Hash previousTransactionHash = transactionOutputIdentifier.getTransactionHash();

                    final boolean isUnique = requiredTransactionOutputs.add(transactionOutputIdentifier);
                    if (! isUnique) { // Two inputs cannot spent the same output...
                        final boolean isAllowedDuplicate = ALLOWED_DUPLICATE_TRANSACTION_HASHES.contains(previousTransactionHash);
                        if (! isAllowedDuplicate) {
                            return null;
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
        multiTimer.mark("blockTxns");

        requiredTransactionOutputs.removeAll(newOutputs); // New outputs created by this block are not added to this UTXO set.
        multiTimer.mark("excludeOutputs");

//        if (! Util.coalesce(blockIsOnMainChain, true)) {
//            return _loadOutputsForAlternateBlock(databaseManager, blockId, requiredTransactionOutputs, upgradeSchedule);
//        }

        return requiredTransactionOutputs;
    }

    public synchronized Boolean finishLoadingOutputsForBlock(final Blockchain blockchain, final Block block, final Long blockHeight, final UpgradeSchedule upgradeSchedule) throws DatabaseException {
        if (_missingOutputIdentifiers.isEmpty()) { return true; }

        final MultiTimer multiTimer = new MultiTimer();
        multiTimer.start();

        final List<TransactionOutputIdentifier> missingOutputIdentifiers = _stepTwo(_missingOutputIdentifiers, blockchain, blockHeight, upgradeSchedule, multiTimer);
        _missingOutputIdentifiers.addAll(missingOutputIdentifiers);

        return missingOutputIdentifiers.isEmpty();
    }

    public synchronized Boolean loadOutputsForBlock(final Blockchain blockchain, final Block block, final Long blockHeight, final UpgradeSchedule upgradeSchedule) throws DatabaseException {
        final MultiTimer multiTimer = new MultiTimer();
        final MutableHashSet<TransactionOutputIdentifier> requiredTransactionOutputs = _stepOne(block, blockHeight, multiTimer);
        if (requiredTransactionOutputs == null) { return false; }

        final int outputCount = requiredTransactionOutputs.getCount();
        _missingOutputIdentifiers.addAll(requiredTransactionOutputs);

        final List<TransactionOutputIdentifier> missingOutputIdentifiers = _stepTwo(_missingOutputIdentifiers, blockchain, blockHeight, upgradeSchedule, multiTimer);
        _missingOutputIdentifiers.addAll(missingOutputIdentifiers);

        final int missingOutputsCount = missingOutputIdentifiers.getCount();
        if (missingOutputsCount > 0) {
            Logger.debug("Failed to load " + missingOutputsCount + "/" + outputCount + " outputs.");
        }
        return missingOutputIdentifiers.isEmpty();
    }

    protected List<TransactionOutputIdentifier> _stepTwo(final List<TransactionOutputIdentifier> transactionOutputIdentifiers, final Blockchain blockchain, final Long blockHeight, final UpgradeSchedule upgradeSchedule, final MultiTimer multiTimer) throws DatabaseException {
        final MutableList<TransactionOutputIdentifier> missingOutputIdentifiers = new MutableArrayList<>();

        // double timeSpentLoadingUnknownPatfoBlockTimes = 0D;
        final MutableHashSet<Sha256Hash> unknownTransactionBlockHeightsSet = new MutableHashSet<>();
        boolean allTransactionOutputsWereLoaded = true;
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = blockchain.getUtxoSet();
        final List<UnspentTransactionOutput> transactionOutputs = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutputs(transactionOutputIdentifiers);
        for (int i = 0; i < transactionOutputs.getCount(); ++i) {
            final TransactionOutputIdentifier transactionOutputIdentifier = transactionOutputIdentifiers.get(i);
            final UnspentTransactionOutput transactionOutput = transactionOutputs.get(i);
            if (transactionOutput == null) {
                missingOutputIdentifiers.add(transactionOutputIdentifier);
                if (allTransactionOutputsWereLoaded) {
                    Logger.debug("Missing UTXO: " + transactionOutputIdentifier);
                }
                allTransactionOutputsWereLoaded = false;
                continue; // Continue processing for pre-loading the UTXO set for pending blocks...
            }

            _transactionOutputs.put(transactionOutputIdentifier, transactionOutput);

            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final Long transactionOutputBlockHeight = transactionOutput.getBlockHeight();
            if ( (transactionOutputBlockHeight == null) || Util.areEqual(transactionOutputBlockHeight, UnspentTransactionOutput.UNKNOWN_BLOCK_HEIGHT) ) {
                unknownTransactionBlockHeightsSet.add(transactionHash);

                // Check PATFO status...
                if (transactionOutput.hasCashToken()) {
                    // final NanoTimer nanoTimer = new NanoTimer();
                    // nanoTimer.start();
                    //
                    // This is highly inefficient if done often, however the times a UTXO's blockHeight is unknown should be very, very rare.
                    // final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                    // final BlockId transactionOutputBlockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);
                    // final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.getMedianTimePast(transactionOutputBlockId);
                    // if (! upgradeSchedule.areCashTokensEnabled(medianBlockTime)) {
                    //     _preActivationTokenForgeries.add(transactionOutputIdentifier);
                    // }
                    //
                    // nanoTimer.stop();
                    // timeSpentLoadingUnknownPatfoBlockTimes += nanoTimer.getMillisecondsElapsed();

                    throw new RuntimeException("Unable to find UTXO Block Height."); // TODO
                }
            }
            else {
                _transactionBlockHeights.put(transactionHash, transactionOutputBlockHeight);

                // Check PATFO status...
                if (transactionOutput.hasCashToken()) {
                    final MedianBlockTime medianBlockTime = blockchain.getMedianBlockTime(blockHeight);
                    if (! upgradeSchedule.areCashTokensEnabled(medianBlockTime)) {
                        _preActivationTokenForgeries.add(transactionOutputIdentifier);
                    }
                }
            }
        }
        multiTimer.mark("loadOutputs");

        // Load the BlockHeights for the unknown Transactions (the previous Transactions being spent by (and outside of) this block)...
        // _populateUnknownTransactionBlockHeights(unknownTransactionBlockHeightsSet, blockHeight); // TODO ?

        multiTimer.stop("blockHeights");

        if (Logger.isDebugEnabled()) {
            Logger.debug("Load UTXOs MultiTimer: " + multiTimer);
            // Logger.debug("timeSpentLoadingUnknownPatfoBlockTimes=" + timeSpentLoadingUnknownPatfoBlockTimes);
        }

        return missingOutputIdentifiers;
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
    public Boolean isCoinbaseTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final UnspentTransactionOutput transactionOutput = _transactionOutputs.get(transactionOutputIdentifier);
        if (transactionOutput == null) { return null; }

        return transactionOutput.isCoinbase();
    }

    /**
     * Adds new outputs created by the provided block and removes outputs spent by the block.
     */
    public synchronized void update(final Block block, final Long blockHeight, final MedianBlockTime medianBlockTime, final UpgradeSchedule upgradeSchedule) {
        final List<Transaction> transactions = block.getTransactions();

        Transaction coinbaseTransaction = null;
        { // Add the new outputs created by the block...
            for (final Transaction transaction : transactions) {
                final Sha256Hash transactionHash = transaction.getHash();
                _transactionBlockHeights.put(transactionHash, blockHeight);

                int outputIndex = 0;
                final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                for (final TransactionOutput transactionOutput : transactionOutputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                    final Boolean isCoinbase = (coinbaseTransaction == null);
                    final UnspentTransactionOutput unspentTransactionOutput = new ImmutableUnspentTransactionOutput(transactionOutput, blockHeight, isCoinbase);
                    _transactionOutputs.put(transactionOutputIdentifier, unspentTransactionOutput);
                    outputIndex += 1;

                    if (transactionOutput.hasCashToken()) {
                        if (! upgradeSchedule.areCashTokensEnabled(medianBlockTime)) {
                            _preActivationTokenForgeries.add(transactionOutputIdentifier);
                        }
                    }
                }

                if (coinbaseTransaction == null) {
                    coinbaseTransaction = transaction;
                }
            }
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

    public List<TransactionOutputIdentifier> getMissingOutputs() {
        return _missingOutputIdentifiers;
    }

    /**
     * Returns true if the TransactionOutput associated with the provided TransactionOutputIdentifier is a CashToken output
     *  that was created before the CashToken activation fork.  CashTokens generated before the activation are not spendable.
     */
    public Boolean isPreActivationTokenForgery(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _preActivationTokenForgeries.contains(transactionOutputIdentifier);
    }

    public synchronized void clear() {
        _missingOutputIdentifiers.clear();
        _transactionOutputs.clear();
        _transactionBlockHeights.clear();
        _preActivationTokenForgeries.clear();
    }
}

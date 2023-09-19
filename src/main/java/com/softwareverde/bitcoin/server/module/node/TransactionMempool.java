package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputFileDbManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.ImmutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidationResult;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Tuple;

public class TransactionMempool {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final Blockchain _blockchain;
    protected final NetworkTime _networkTime;
    protected final UnspentTransactionOutputFileDbManager _utxoManager;
    protected final MutableMap<Sha256Hash, TransactionWithFee> _transactionHashes = new MutableHashMap<>();
    protected final MutableList<Transaction> _transactions = new MutableArrayList<>();
    protected final MutableMap<Sha256Hash, MutableHashSet<Sha256Hash>> _invalidTransactionDependencies = new MutableHashMap<>();
    protected final MutableMap<Sha256Hash, Transaction> _invalidTransactions = new MutableHashMap<>();
    protected long _minFee = 0L;
    protected long _totalFees = 0L;
    protected int _signatureOperationCount = 0;
    protected long _blockHeight;

    protected long _getTotalInputValue(final Transaction transaction, final UnspentTransactionOutputContext utxoContext) {
        long totalAmount = 0L;
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);

            final TransactionOutput unspentTransactionOutput = utxoContext.getTransactionOutput(transactionOutputIdentifier);
            if (unspentTransactionOutput == null) { return 0L; }

            totalAmount += unspentTransactionOutput.getAmount();
        }
        return totalAmount;
    }

    protected UnspentTransactionOutputContext _getUnspentTransactionOutputContext(final Transaction transaction, final boolean allowUnfound) {
        final MutableHashMap<TransactionOutputIdentifier, UnspentTransactionOutput> unspentOutputs = new MutableHashMap<>();
        try {
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                if (_transactionHashes.containsKey(transactionHash)) {
                    final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

                    final TransactionWithFee previousTransaction = _transactionHashes.get(transactionHash);
                    final TransactionOutput transactionOutput = previousTransaction.transaction.getTransactionOutput(outputIndex);
                    if (transactionOutput == null) {
                        if (! allowUnfound) { return null; }

                        unspentOutputs.put(transactionOutputIdentifier, null);
                    }
                    else {
                        final UnspentTransactionOutput unspentTransactionOutput = new ImmutableUnspentTransactionOutput(transactionOutput, _blockHeight, false);
                        unspentOutputs.put(transactionOutputIdentifier, unspentTransactionOutput);
                    }
                }
                else {
                    final UnspentTransactionOutput unspentTransactionOutput = _utxoManager.getUnspentTransactionOutput(transactionOutputIdentifier);
                    if ( (! allowUnfound) && (unspentTransactionOutput == null) ) { return null; }

                    unspentOutputs.put(transactionOutputIdentifier, unspentTransactionOutput);
                }
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }

        return new UnspentTransactionOutputContext() {
            @Override
            public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
                return unspentOutputs.get(transactionOutputIdentifier);
            }

            @Override
            public Long getBlockHeight(final TransactionOutputIdentifier transactionOutputIdentifier) {
                final UnspentTransactionOutput unspentTransactionOutput = unspentOutputs.get(transactionOutputIdentifier);
                return unspentTransactionOutput.getBlockHeight();
            }

            @Override
            public Boolean isCoinbaseTransactionOutput(TransactionOutputIdentifier transactionOutputIdentifier) {
                final UnspentTransactionOutput unspentTransactionOutput = unspentOutputs.get(transactionOutputIdentifier);
                return unspentTransactionOutput.isCoinbase();
            }

            @Override
            public Boolean isPreActivationTokenForgery(TransactionOutputIdentifier transactionOutputIdentifier) {
                final UnspentTransactionOutput transactionOutput = unspentOutputs.get(transactionOutputIdentifier);
                if (transactionOutput.hasCashToken()) {
                    final Long medianTimePastBlockHeight = (transactionOutput.getBlockHeight() - 1L);
                    final MedianBlockTime medianBlockTime = _blockchain.getMedianBlockTime(medianTimePastBlockHeight);
                    if (! _upgradeSchedule.areCashTokensEnabled(medianBlockTime)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    protected boolean _addTransaction(final Transaction transaction) {
        final UnspentTransactionOutputContext utxoContext;
        try {
            utxoContext = _getUnspentTransactionOutputContext(transaction, false);
            if (utxoContext == null) { return false; }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return false;
        }

        final Long blockHeight = (_blockchain.getHeadBlockHeaderHeight() + 1L);
        final TransactionValidator transactionValidator = new TransactionValidatorCore(_upgradeSchedule, _blockchain, _networkTime, utxoContext);
        final TransactionValidationResult validationResult = transactionValidator.validateTransaction(blockHeight, transaction);

        final int maximumSignatureOperationCount = (BitcoinConstants.getBlockMaxByteCount() / Block.MIN_BYTES_PER_SIGNATURE_OPERATION);
        final int totalSignatureOperationCount = (_signatureOperationCount + validationResult.signatureOperationCount);
        if (totalSignatureOperationCount > maximumSignatureOperationCount) {
            return false;
        }

        final long totalOutputValue = transaction.getTotalOutputValue();
        final long totalInputValue = _getTotalInputValue(transaction, utxoContext);
        final long fee = (totalInputValue - totalOutputValue);
        if (fee < 0L) { return false; }
        if (fee < _minFee) { return false; }

        final Sha256Hash transactionHash = transaction.getHash();
        _transactionHashes.put(transactionHash, new TransactionWithFee(transaction, fee));
        _transactions.add(transaction);
        _totalFees += fee;
        _signatureOperationCount += validationResult.signatureOperationCount;
        return true;
    }

    protected void _clear() {
        _transactionHashes.clear();
        _transactions.clear();
        _totalFees = 0L;
        _signatureOperationCount = 0;
        _invalidTransactionDependencies.clear();
        _invalidTransactions.clear();
        _blockHeight = (_blockchain.getHeadBlockHeaderHeight() + 1L);
    }

    public TransactionMempool(final Blockchain blockchain, final UpgradeSchedule upgradeSchedule, final NetworkTime networkTime, final UnspentTransactionOutputFileDbManager utxoManager) {
        _upgradeSchedule = upgradeSchedule;
        _networkTime = networkTime;
        _blockchain = blockchain;
        _utxoManager = utxoManager;

        _blockHeight = (_blockchain.getHeadBlockHeaderHeight() + 1L);
    }

    public synchronized boolean contains(final Sha256Hash transactionHash) {
        return ( _transactionHashes.containsKey(transactionHash) || _invalidTransactionDependencies.containsKey(transactionHash) );
    }

    public synchronized void revalidate() {
        if (_transactions.isEmpty()) { return; }

        final List<Transaction> transactions = new MutableArrayList<>(_transactions);
        _clear(); // NOTE: Can technically lose transactions if a new block is found before prevout dependencies received...
        for (final Transaction transaction : transactions) {
            _addTransaction(transaction);
        }
    }

    public synchronized boolean addTransaction(final Transaction transaction) {
        final Sha256Hash transactionHash = transaction.getHash();
        final boolean wasAdded = _addTransaction(transaction);
        if (wasAdded) {

            _invalidTransactionDependencies.mutableVisit(new MutableMap.MutableVisitor<>() {
                @Override
                public boolean run(final Tuple<Sha256Hash, MutableHashSet<Sha256Hash>> entry) {
                    if (entry.second.remove(transactionHash)) {
                        if (entry.second.isEmpty()) {
                            // Attempt to add the previously invalid transaction now that all dependencies have been found.
                            final Transaction previouslyInvalidTransaction = _invalidTransactions.remove(entry.first);
                            entry.first = null; // Remove item.
                            if (previouslyInvalidTransaction != null) {
                                _addTransaction(previouslyInvalidTransaction);
                            }
                        }
                    }
                    return true;
                }
            });
        }
        else {
            MutableHashSet<Sha256Hash> prevoutTransactionHashes = _invalidTransactionDependencies.get(transactionHash);
            if (prevoutTransactionHashes == null) {
                prevoutTransactionHashes = new MutableHashSet<>();
                _invalidTransactionDependencies.put(transactionHash, prevoutTransactionHashes);
            }

            final UnspentTransactionOutputContext utxoContext = _getUnspentTransactionOutputContext(transaction, true);
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                if (utxoContext.getTransactionOutput(transactionOutputIdentifier) == null) {
                    prevoutTransactionHashes.add(transactionOutputIdentifier.getTransactionHash());
                }
            }
        }
        return wasAdded;
    }

    public synchronized TransactionWithFee getTransaction(final Sha256Hash transactionHash) {
        return _transactionHashes.get(transactionHash);
    }

    public synchronized void setMinimumFee(final long fee) {
        if (fee < 0L) { return; }
        _minFee = fee;
    }

    public synchronized List<TransactionWithFee> getTransactions() {
        return new MutableArrayList<>(_transactionHashes.getValues());
    }

    public synchronized long getTotalFees() {
        return _totalFees;
    }

    public synchronized int getSignatureOperationCount() {
        return _signatureOperationCount;
    }

    public synchronized int getCount() {
        return _transactions.getCount();
    }

    public synchronized long getBlockHeight() {
        return _blockHeight;
    }

    public synchronized void clear() {
        _clear();
    }
}

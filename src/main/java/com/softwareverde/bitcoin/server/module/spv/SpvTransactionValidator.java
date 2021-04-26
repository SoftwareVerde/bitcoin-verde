package com.softwareverde.bitcoin.server.module.spv;

import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptType;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SpvTransactionValidator {
    public interface ValidationCallback {
        void onValidationComplete(Transaction transaction, Boolean isValid);
        void onFailure(Transaction transaction);
    }

    public interface TransactionAccumulatorCallback {
        void onTransactionsLoaded(Map<Sha256Hash, Transaction> previousTransactions);
    }

    public interface TransactionAccumulator {
        void getTransactions(List<Sha256Hash> transactionHashes, TransactionAccumulatorCallback callback);
    }

    protected final TransactionAccumulator _transactionAccumulator;
    protected final ValidationCallback _validationCallback;

    protected Integer _maxRecursionDepth = 0;

    protected static LockingScript _getSlpScript(final Transaction transaction) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final boolean isSlpTransaction = SlpScriptInflater.matchesSlpFormat(lockingScript);
        return (isSlpTransaction ? lockingScript : null);
    }

    protected BigInteger _getSlpAmount(final SlpTokenId slpTokenId, final Transaction transaction, final Integer transactionOutputIndex) {
        final LockingScript slpScript = _getSlpScript(transaction);
        if (slpScript == null) { return BigInteger.ZERO; }

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(slpScript);
        if (slpScriptType == null) { return BigInteger.ZERO; }

        switch (slpScriptType) {
            case MINT: {
                if (! Util.areEqual(SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX, transactionOutputIndex)) { return BigInteger.ZERO; }

                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(slpScript);
                if (slpMintScript == null) { return BigInteger.ZERO; }

                if (! Util.areEqual(slpMintScript.getTokenId(), slpTokenId)) { return BigInteger.ZERO; }

                return slpMintScript.getTokenCount();
            }

            case SEND: {
                final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(slpScript);
                if (slpSendScript == null) { return BigInteger.ZERO; }

                if (! Util.areEqual(slpSendScript.getTokenId(), slpTokenId)) { return BigInteger.ZERO; }

                final BigInteger sendAmount = slpSendScript.getAmount(transactionOutputIndex);
                if (sendAmount == null) { return BigInteger.ZERO; }
                return sendAmount;
            }

            case GENESIS: {
                if (! Util.areEqual(SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX, transactionOutputIndex)) { return BigInteger.ZERO; }

                final SlpGenesisScript slpGenesisScript = slpScriptInflater.genesisScriptFromScript(slpScript);
                if (slpGenesisScript == null) { return BigInteger.ZERO; }

                final SlpTokenId genesisTokenId = SlpTokenId.wrap(transaction.getHash());
                if (! Util.areEqual(genesisTokenId, slpTokenId)) { return BigInteger.ZERO; }

                return slpGenesisScript.getTokenCount();
            }
        }

        return BigInteger.ZERO;
    }

    protected static SlpTokenId _getTokenId(final Transaction transaction) {
        final LockingScript slpScript = _getSlpScript(transaction);
        if (slpScript == null) { return null; }

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(slpScript);
        if (slpScriptType == null) { return null; }

        switch (slpScriptType) {
            case MINT: {
                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(slpScript);
                if (slpMintScript == null) { return null; }

                return slpMintScript.getTokenId();
            }

            case SEND: {
                final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(slpScript);
                if (slpSendScript == null) { return null; }

                return slpSendScript.getTokenId();
            }

            case GENESIS: {
                final SlpGenesisScript slpGenesisScript = slpScriptInflater.genesisScriptFromScript(slpScript);
                if (slpGenesisScript == null) { return null; }

                return SlpTokenId.wrap(transaction.getHash());
            }
        }

        return null;
    }

    protected static List<Sha256Hash> _getPreviousTransactionHashes(final Transaction transaction) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

        final HashSet<Sha256Hash> uniqueTransactionHashes = new HashSet<Sha256Hash>();
        for (final TransactionInput transactionInput : transactionInputs) {
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            uniqueTransactionHashes.add(previousTransactionHash);
        }
        return new MutableList<Sha256Hash>(uniqueTransactionHashes);
    }

    protected static BigInteger _calculateTotalSlpAmountSpent(final Transaction transaction) {
        final LockingScript slpScript = _getSlpScript(transaction);

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(slpScript);

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        BigInteger total = BigInteger.ZERO;
        for (int i = 1; i < transactionOutputs.getCount(); ++i) {
            final BigInteger slpAmount = slpSendScript.getAmount(i);
            if (slpAmount != null) {
                total = total.add(slpAmount);
            }
        }
        return total;
    }

    class ValidationMetaData {
        public final SlpTokenId slpTokenId;
        public final Transaction rootTransaction;
        public final ValidationCallback validationCallback;

        private final AtomicInteger pendingCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicBoolean isFinished = new AtomicBoolean(false);

        private void _finish(final Boolean wasSuccess) {
            if (isFinished.compareAndSet(false, true)) {
                this.validationCallback.onValidationComplete(this.rootTransaction, wasSuccess);
            }
        }

        public ValidationMetaData(final Transaction rootTransaction, final ValidationCallback validationCallback) {
            this.slpTokenId = _getTokenId(rootTransaction);
            this.rootTransaction = rootTransaction;
            this.validationCallback = validationCallback;
        }

        public synchronized void onNewRequest() {
            this.pendingCount.incrementAndGet();
        }

        public synchronized void onSuccess() {
            this.pendingCount.decrementAndGet();
            this.successCount.incrementAndGet();

            _finish(true);
        }

        public synchronized void onFailure() {
            final int pendingCount = this.pendingCount.decrementAndGet();
            this.failureCount.incrementAndGet();

            if (pendingCount < 1) {
                _finish(false);
            }
        }

        public synchronized void onFailure(final Boolean abort) {
            if (abort) {
                this.pendingCount.set(0);
                this.failureCount.incrementAndGet();

                _finish(false);
            }
            else {
                final int pendingCount = this.pendingCount.decrementAndGet();
                this.failureCount.incrementAndGet();

                if (pendingCount < 1) {
                    _finish(false);
                }
            }
        }

        public synchronized void onRecursionMax() {
            final int pendingCount = this.pendingCount.decrementAndGet();

            if (pendingCount < 1) {
                _finish(true);
            }
        }

        public synchronized void onOther() {
            final int pendingCount = this.pendingCount.decrementAndGet();

            if (pendingCount < 1) {
                _finish(true);
            }
        }
    }

    protected void _validateMintTransaction(final ValidationMetaData validationMetaData, final Transaction transaction, final Integer recursionCount, final Runnable onSuccess) {
        final List<Sha256Hash> previousTransactionHashes = _getPreviousTransactionHashes(transaction);

        final SlpTokenId slpTokenId = _getTokenId(transaction);
        if (! Util.areEqual(validationMetaData.slpTokenId, slpTokenId)) {
            validationMetaData.onFailure();
            return;
        }

        // Check for recursion max...
        if ( (slpTokenId != null) && (! previousTransactionHashes.isEmpty()) ) {
            if ( (_maxRecursionDepth >= 0) && (recursionCount >= _maxRecursionDepth) ) {
                onSuccess.run();
                validationMetaData.onRecursionMax();
                return;
            }
        }

        validationMetaData.onNewRequest();

        // Request the previous transactions and then check if one is the genesis.
        //  If the genesis is found, then validate the minted amount is at least equal to the spent amount.
        //  If a mint is found, accumulate the minted amount towards the expenditure, and recurse
        //  If none of the previous transactions are a genesis or a mint, then recurse
        _transactionAccumulator.getTransactions(previousTransactionHashes, new TransactionAccumulatorCallback() {
            @Override
            public void onTransactionsLoaded(final Map<Sha256Hash, Transaction> previousTransactions) {
                // If the accumulator was unable to connect then mark the validation as failed.
                if (previousTransactions == null) {
                    validationMetaData.onFailure();
                    return;
                }

                // Check for a genesis or another mint to recurse on...
                Sha256Hash previousMintTransactionHash = null;
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

                    final Transaction previousTransaction = previousTransactions.get(previousTransactionHash);
                    if (previousTransaction == null) { continue; } // One of the transactions weren't available; if none of the others are a genesis or mint, then fail the validation after the loop.

                    final SlpTokenId previousSlpTokenId = _getTokenId(previousTransaction);
                    if (! Util.areEqual(slpTokenId, previousSlpTokenId)) { continue; } // This previous Transaction is not an SLP Transaction, so ignore it.

                    final LockingScript previousSlpTransactionScript = _getSlpScript(previousTransaction);
                    if (previousSlpTransactionScript == null) { continue; } // This previous Transaction had an invalid script, so ignore it.

                    final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(previousSlpTransactionScript);

                    if (slpScriptType == SlpScriptType.GENESIS) { // If a valid genesis is found, then the mint is successful.
                        onSuccess.run();
                        validationMetaData.onOther();
                        return;
                    }

                    if (slpScriptType == SlpScriptType.MINT) { // If one of the inputs is another mint, then recurse on the previous mint.
                        previousMintTransactionHash = previousTransactionHash;
                        break;
                    }
                }

                // None of the previous transactions were a genesis or a mint.  The mint is definitely invalid.
                if (previousMintTransactionHash == null) {
                    validationMetaData.onFailure(true);
                    return;
                }

                if ( (_maxRecursionDepth >= 0) && (recursionCount >= _maxRecursionDepth) ) { // Check for the recursion limit.
                    onSuccess.run();
                    validationMetaData.onRecursionMax();
                    return;
                }

                // Validate the previous mint transaction...
                final Transaction parentMintTransaction = previousTransactions.get(previousMintTransactionHash);
                _validateMintTransaction(validationMetaData, parentMintTransaction, (recursionCount + 1), onSuccess);
            }
        });
    }

    protected void _validateSendTransaction(final ValidationMetaData validationMetaData, final Transaction transaction, final Integer recursionCount, final BigInteger totalSlpAmountSent, final Container<BigInteger> totalReceived, final Runnable onSuccess) {
        final List<Sha256Hash> previousTransactionHashes = _getPreviousTransactionHashes(transaction);

        final SlpTokenId slpTokenId = _getTokenId(transaction);
        if (! Util.areEqual(validationMetaData.slpTokenId, slpTokenId)) {
            validationMetaData.onFailure();
            return;
        }

        // Check for recursion max...
        if ( (slpTokenId != null) && (! previousTransactionHashes.isEmpty()) ) {
            if ( (_maxRecursionDepth >= 0) && (recursionCount >= _maxRecursionDepth) ) {
                onSuccess.run();
                validationMetaData.onRecursionMax();
                return;
            }
        }

        validationMetaData.onNewRequest();

        // Request the previous transactions and then check if one is the genesis.
        //  If none are the genesis, and one is a another mint, recurse on the previous mint transaction until the genesis is found.
        //  If none of the previous transactions are a genesis or a mint, then the mint is invalid.
        _transactionAccumulator.getTransactions(previousTransactionHashes, new TransactionAccumulatorCallback() {
            @Override
            public void onTransactionsLoaded(final Map<Sha256Hash, Transaction> previousTransactions) {
                // If the accumulator was unable to connect then mark the validation as failed.
                if (previousTransactions == null) {
                    validationMetaData.onFailure();
                    return;
                }

                // Check for a genesis, mint, or other send to recurse on...
                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
                    final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

                    final Transaction previousTransaction = previousTransactions.get(previousTransactionHash);
                    if (previousTransaction == null) { continue; } // One of the transactions weren't available; if none of the others are a genesis or mint, then fail the validation after the loop.

                    final SlpTokenId previousSlpTokenId = _getTokenId(previousTransaction);
                    if (! Util.areEqual(slpTokenId, previousSlpTokenId)) { continue; } // This previous Transaction is not an SLP Transaction, so ignore it.

                    final LockingScript previousSlpTransactionScript = _getSlpScript(previousTransaction);
                    if (previousSlpTransactionScript == null) { continue; } // This previous Transaction had an invalid script, so ignore it.

                    final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
                    final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(previousSlpTransactionScript);

                    if (slpScriptType == SlpScriptType.GENESIS) { // If a valid genesis is found, then the mint is successful.
                        final SlpGenesisScript slpGenesisScript = slpScriptInflater.genesisScriptFromScript(previousSlpTransactionScript);
                        final BigInteger genesisTokenAmount = slpGenesisScript.getTokenCount();
                        final Boolean isValid = (totalSlpAmountSent.compareTo(genesisTokenAmount) <= 0);
                        if (isValid) {
                            validationMetaData.onSuccess();
                        }
                        else {
                            validationMetaData.onFailure();
                        }
                        return;
                    }

                    if (slpScriptType == SlpScriptType.MINT) { // If one of the inputs is another mint, then recurse on the previous mint.
                        final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(previousSlpTransactionScript);

                        // If the prevout was the recipient of the mint then validate it's a valid mint, then add its tokens.
                        if (Util.areEqual(SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX, previousTransactionOutputIndex)) {
                            _validateMintTransaction(validationMetaData, previousTransaction, (recursionCount + 1), new Runnable() {
                                @Override
                                public void run() {
                                    synchronized (totalReceived) {
                                        totalReceived.value = totalReceived.value.add(slpMintScript.getTokenCount());
                                    }

                                    if (totalReceived.value.compareTo(totalSlpAmountSent) >= 0) {
                                        onSuccess.run();
                                    }

                                    validationMetaData.onOther();
                                }
                            });
                        }

                        continue;
                    }

                    if (slpScriptType == SlpScriptType.SEND) { // If the input is from another spend, then recurse on the spend.
                        final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(previousSlpTransactionScript);

                        // Validate that the total inputs are at least the total outputs...
                        final BigInteger totalSlpSentInPreviousTransaction = _calculateTotalSlpAmountSpent(previousTransaction);
                        final Container<BigInteger> previousTransactionReceived = new Container<>(BigInteger.ZERO);

                        _validateSendTransaction(validationMetaData, previousTransaction, (recursionCount + 1), totalSlpSentInPreviousTransaction, previousTransactionReceived, new Runnable() {
                            @Override
                            public void run() {
                                synchronized (totalReceived) {
                                    final BigInteger inputAmount = slpSendScript.getAmount(previousTransactionOutputIndex);
                                    if (inputAmount != null) {
                                        totalReceived.value = totalReceived.value.add(inputAmount);
                                    }
                                }

                                if (totalReceived.value.compareTo(totalSlpAmountSent) >= 0) {
                                    onSuccess.run();
                                }
                                validationMetaData.onOther();
                            }
                        });
                    }
                }

                validationMetaData.onFailure();
            }
        });
    }

    public SpvTransactionValidator(final TransactionAccumulator transactionAccumulator, final ValidationCallback validationCallback) {
        _transactionAccumulator = transactionAccumulator;
        _validationCallback = validationCallback;
    }

    public void setMaxRecursionDepth(final Integer recursionCount) {
        _maxRecursionDepth = recursionCount;
    }

    public Integer getRecursionCount() {
        return _maxRecursionDepth;
    }

    public void validateTransaction(final Transaction transactionToValidate) {
        final LockingScript slpScript = _getSlpScript(transactionToValidate);
        final boolean isSlpTransaction = (slpScript != null);

        if (! isSlpTransaction) {
            _validationCallback.onValidationComplete(transactionToValidate, true);
            return;
        }

        final SlpTokenId slpTokenId = _getTokenId(transactionToValidate);
        if (slpTokenId == null) {
            _validationCallback.onValidationComplete(transactionToValidate, false);
            return;
        }

        final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(slpScript);

        if (slpScriptType == SlpScriptType.MINT) {
            final ValidationMetaData validationMetaData = new ValidationMetaData(transactionToValidate, _validationCallback);
            _validateMintTransaction(validationMetaData, transactionToValidate, 0, new Runnable() {
                @Override
                public void run() {
                    validationMetaData.onSuccess();
                }
            });
            return;
        }

        if (slpScriptType == SlpScriptType.SEND) {
            final BigInteger totalSlpSent = _calculateTotalSlpAmountSpent(transactionToValidate);
            final ValidationMetaData validationMetaData = new ValidationMetaData(transactionToValidate, _validationCallback);
            _validateSendTransaction(validationMetaData, transactionToValidate, 0, totalSlpSent, new Container<BigInteger>(BigInteger.ZERO), new Runnable() {
                @Override
                public void run() {
                    validationMetaData.onSuccess();
                }
            });
            return;
        }

        _validationCallback.onValidationComplete(transactionToValidate, true);
    }
}

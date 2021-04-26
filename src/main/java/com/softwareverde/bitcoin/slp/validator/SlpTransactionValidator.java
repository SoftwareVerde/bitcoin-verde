package com.softwareverde.bitcoin.slp.validator;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptType;
import com.softwareverde.bitcoin.transaction.script.slp.commit.SlpCommitScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class SlpTransactionValidator {
    protected final Integer _maxRecursionDepth = 1024;
    protected final SlpTransactionValidationCache _validationCache;
    protected final TransactionAccumulator _transactionAccumulator;
    protected Boolean _allowUnconfirmedTransactions = true;

    protected SlpScript _getSlpScript(final Transaction transaction) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript slpLockingScript = transactionOutput.getLockingScript();

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        return slpScriptInflater.fromLockingScript(slpLockingScript);
    }

    protected Map<Sha256Hash, Transaction> _getTransactions(final List<TransactionInput> transactionInputs, final Boolean allowUnconfirmedTransactions) {
        final ImmutableListBuilder<Sha256Hash> transactionHashes = new ImmutableListBuilder<Sha256Hash>(transactionInputs.getCount());
        for (final TransactionInput transactionInput : transactionInputs) {
            final Sha256Hash transactionHash = transactionInput.getPreviousOutputTransactionHash();
            transactionHashes.add(transactionHash);
        }

        return _transactionAccumulator.getTransactions(transactionHashes.build(), allowUnconfirmedTransactions);
    }

    protected Boolean _validateRecursiveTransactions(final Map<SlpScriptType, ? extends List<Transaction>> recursiveTransactionsToValidate, final Integer recursionDepth) {
        if (recursionDepth >= _maxRecursionDepth) {
            if (Logger.isDebugEnabled()) {
                Logger.debug("Max SLP validation recursion depth reached. (" + recursionDepth + ")");
                for (final SlpScriptType slpScriptType : recursiveTransactionsToValidate.keySet()) {
                    for (final Transaction transaction : recursiveTransactionsToValidate.get(slpScriptType)) {
                        Logger.debug(slpScriptType + " " + transaction.getHash() + " assumed valid.");
                    }
                }
            }
            return true;
        }

        for (final SlpScriptType slpScriptType : recursiveTransactionsToValidate.keySet()) {
            final List<Transaction> transactions = recursiveTransactionsToValidate.get(slpScriptType);

            switch (slpScriptType) {
                case GENESIS: {
                    for (final Transaction transaction : transactions) {
                        final Boolean isCachedAsValid = _validationCache.isValid(transaction.getHash());
                        if (isCachedAsValid != null) {
                            if (isCachedAsValid) { continue; }
                            else { return false; }
                        }

                        final Boolean isValid = _validateSlpGenesisTransaction(transaction);
                        if (! isValid) { return false; }
                    }
                } break;
                case MINT: {
                    for (final Transaction transaction : transactions) {
                        final Boolean isCachedAsValid = _validationCache.isValid(transaction.getHash());
                        if (isCachedAsValid != null) {
                            if (isCachedAsValid) { continue; }
                            else { return false; }
                        }

                        final Boolean isValid = _validateSlpMintTransaction(transaction, null, (recursionDepth + 1));
                        if (! isValid) { return false; }
                    }
                } break;
                case SEND: {
                    for (final Transaction transaction : transactions) {
                        final Boolean isCachedAsValid = _validationCache.isValid(transaction.getHash());
                        if (isCachedAsValid != null) {
                            if (isCachedAsValid) { continue; }
                            else { return false; }
                        }

                        final Boolean isValid = _validateSlpSendTransaction(transaction, null, (recursionDepth + 1));
                        if (! isValid) { return false; }
                    }
                } break;
                case COMMIT: {
                    for (final Transaction transaction : transactions) {
                        final Boolean isCachedAsValid = _validationCache.isValid(transaction.getHash());
                        if (isCachedAsValid != null) {
                            if (isCachedAsValid) { continue; }
                            else { return false; }
                        }

                        final Boolean isValid = _validateSlpCommitTransaction(transaction);
                        if (! isValid) { return false; }
                    }
                } break;
            }
        }

        return true;
    }

    protected Boolean _validateSlpGenesisScript(final SlpGenesisScript slpGenesisScript) {
        return (slpGenesisScript != null);
    }

    protected Boolean _validateSlpGenesisTransaction(final Transaction transaction) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript slpLockingScript = transactionOutput.getLockingScript();

        if (! SlpScriptInflater.matchesSlpFormat(slpLockingScript)) { return null; }

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        final SlpScript slpScript = slpScriptInflater.fromLockingScript(slpLockingScript);
        if (slpScript == null) { return false; }

        return (slpScript instanceof SlpGenesisScript);
    }

    protected Boolean _validateSlpCommitScript(final SlpCommitScript slpCommitScript) {
        return (slpCommitScript != null);
    }

    protected Boolean _validateSlpCommitTransaction(final Transaction transaction) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript slpLockingScript = transactionOutput.getLockingScript();

        if (! SlpScriptInflater.matchesSlpFormat(slpLockingScript)) { return null; }

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        final SlpScript slpScript = slpScriptInflater.fromLockingScript(slpLockingScript);
        if (slpScript == null) { return false; }

        return (slpScript instanceof SlpCommitScript);
    }

    protected Boolean _validateSlpMintTransaction(final Transaction transaction, final SlpMintScript nullableSlpMintScript, final Integer recursionDepth) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final SlpMintScript slpMintScript = ((nullableSlpMintScript != null) ? nullableSlpMintScript : ((SlpMintScript) _getSlpScript(transaction)));
        final SlpTokenId slpTokenId = slpMintScript.getTokenId();

        final Map<Sha256Hash, Transaction> previousTransactions = _getTransactions(transactionInputs, _allowUnconfirmedTransactions);
        if (previousTransactions == null) { return false; }

        final HashMap<SlpScriptType, MutableList<Transaction>> recursiveTransactionsToValidate = new HashMap<SlpScriptType, MutableList<Transaction>>();

        boolean hasBaton = false;

        for (final TransactionInput transactionInput : transactionInputs) {
            final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

            final Transaction previousTransaction = previousTransactions.get(previousTransactionHash);
            if (previousTransaction == null) {
                Logger.debug("Could not find previous Transaction: " + previousTransactionHash);
                return false;
            }

            final SlpScript previousTransactionSlpScript = _getSlpScript(previousTransaction);
            final boolean isSlpTransaction = (previousTransactionSlpScript != null);
            if (! isSlpTransaction) { continue; }

            final SlpScriptType slpScriptType = previousTransactionSlpScript.getType();
            if (slpScriptType == SlpScriptType.GENESIS) {
                if (! Util.areEqual(slpTokenId, SlpTokenId.wrap(previousTransactionHash))) { continue; }

                final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) previousTransactionSlpScript;
                if (Util.areEqual(previousTransactionOutputIndex, slpGenesisScript.getBatonOutputIndex())) {
                    ConstUtil.addToListMap(SlpScriptType.GENESIS, previousTransaction, recursiveTransactionsToValidate);
                    final Boolean isValid = _validateRecursiveTransactions(recursiveTransactionsToValidate, recursionDepth + 1);
                    recursiveTransactionsToValidate.clear();
                    if (isValid) {
                        hasBaton = true;
                        break;
                    }
                }
            }
            else if (slpScriptType == SlpScriptType.MINT) {
                final SlpMintScript previousSlpMintScript = (SlpMintScript) previousTransactionSlpScript;
                if (! Util.areEqual(slpTokenId, previousSlpMintScript.getTokenId())) { continue; }

                if (Util.areEqual(previousTransactionOutputIndex, previousSlpMintScript.getBatonOutputIndex())) {
                    ConstUtil.addToListMap(SlpScriptType.MINT, previousTransaction, recursiveTransactionsToValidate);
                    final Boolean isValid = _validateRecursiveTransactions(recursiveTransactionsToValidate, recursionDepth + 1);
                    recursiveTransactionsToValidate.clear();
                    if (isValid) {
                        hasBaton = true;
                        break;
                    }
                }
            }
            else if (slpScriptType == SlpScriptType.SEND) {
                // Nothing.
            }
            else if (slpScriptType == SlpScriptType.COMMIT) {
                // Nothing.
            }
        }

        return hasBaton;
    }

    protected Boolean _validateSlpSendTransaction(final Transaction transaction, final SlpSendScript nullableSlpSendScript, final Integer recursionDepth) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final SlpSendScript slpSendScript = ((nullableSlpSendScript != null) ? nullableSlpSendScript : ((SlpSendScript) _getSlpScript(transaction)));
        final SlpTokenId slpTokenId = slpSendScript.getTokenId();

        final BigInteger totalSendAmount = slpSendScript.getTotalAmount();
        final Map<Sha256Hash, Transaction> previousTransactions = _getTransactions(transactionInputs, _allowUnconfirmedTransactions);
        if (previousTransactions == null) { return false; }

        BigInteger totalSlpAmountReceived = BigInteger.ZERO;
        for (final TransactionInput transactionInput : transactionInputs) {
            final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

            final Transaction previousTransaction = previousTransactions.get(previousTransactionHash);
            if (previousTransaction == null) {
                Logger.debug("Could not find previous Transaction: " + previousTransactionHash);
                return false;
            }

            final SlpScript previousTransactionSlpScript = _getSlpScript(previousTransaction);
            final boolean isSlpTransaction = (previousTransactionSlpScript != null);
            if (! isSlpTransaction) { continue; }

            final SlpScriptType slpScriptType = previousTransactionSlpScript.getType();
            if (slpScriptType == SlpScriptType.GENESIS) {
                if (! Util.areEqual(slpTokenId, SlpTokenId.wrap(previousTransactionHash))) { continue; }

                final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) previousTransactionSlpScript;
                if (Util.areEqual(previousTransactionOutputIndex, SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX)) {

                    final Boolean isValid;
                    {
                        final HashMap<SlpScriptType, MutableList<Transaction>> recursiveTransactionsToValidate = new HashMap<SlpScriptType, MutableList<Transaction>>();
                        ConstUtil.addToListMap(SlpScriptType.GENESIS, previousTransaction, recursiveTransactionsToValidate);
                        isValid = _validateRecursiveTransactions(recursiveTransactionsToValidate, (recursionDepth + 1));
                        recursiveTransactionsToValidate.clear();
                    }

                    if (isValid) {
                        totalSlpAmountReceived = totalSlpAmountReceived.add(slpGenesisScript.getTokenCount());
                    }
                }
            }
            else if (slpScriptType == SlpScriptType.MINT) {
                final SlpMintScript slpMintScript = (SlpMintScript) previousTransactionSlpScript;
                if (! Util.areEqual(slpTokenId, slpMintScript.getTokenId())) { continue; }

                if (Util.areEqual(previousTransactionOutputIndex, SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX)) {
                    final Boolean isValid;
                    {
                        final HashMap<SlpScriptType, MutableList<Transaction>> recursiveTransactionsToValidate = new HashMap<SlpScriptType, MutableList<Transaction>>();
                        ConstUtil.addToListMap(SlpScriptType.MINT, previousTransaction, recursiveTransactionsToValidate);
                        isValid = _validateRecursiveTransactions(recursiveTransactionsToValidate, (recursionDepth + 1));
                        recursiveTransactionsToValidate.clear();
                    }

                    if (isValid) {
                        totalSlpAmountReceived = totalSlpAmountReceived.add(slpMintScript.getTokenCount());
                    }
                }
            }
            else if (slpScriptType == SlpScriptType.SEND) {
                final SlpSendScript previousTransactionSlpSendScript = (SlpSendScript) previousTransactionSlpScript;
                if (! Util.areEqual(slpTokenId, previousTransactionSlpSendScript.getTokenId())) { continue; }

                final Boolean isValid;
                {
                    final HashMap<SlpScriptType, MutableList<Transaction>> recursiveTransactionsToValidate = new HashMap<SlpScriptType, MutableList<Transaction>>();
                    ConstUtil.addToListMap(SlpScriptType.SEND, previousTransaction, recursiveTransactionsToValidate);
                    isValid = _validateRecursiveTransactions(recursiveTransactionsToValidate, (recursionDepth + 1));
                    recursiveTransactionsToValidate.clear();
                }

                if (isValid) {
                    final BigInteger inputAmount = previousTransactionSlpSendScript.getAmount(previousTransactionOutputIndex);
                    if (inputAmount != null) {
                        totalSlpAmountReceived = totalSlpAmountReceived.add(inputAmount);
                    }
                }
            }
            else if (slpScriptType == SlpScriptType.COMMIT) {
                // Nothing.
            }
        }

        final boolean isValid = (totalSlpAmountReceived.compareTo(totalSendAmount) >= 0);
        _validationCache.setIsValid(transaction.getHash(), isValid);
        return isValid;
    }

    public SlpTransactionValidator(final TransactionAccumulator transactionAccumulator) {
        _transactionAccumulator = transactionAccumulator;
        _validationCache = new HashMapSlpTransactionValidationCache();
    }

    public SlpTransactionValidator(final TransactionAccumulator transactionAccumulator, final SlpTransactionValidationCache validationCache) {
        _transactionAccumulator = transactionAccumulator;
        _validationCache = (validationCache != null ? validationCache : new HashMapSlpTransactionValidationCache());
    }

    public Boolean validateTransaction(final Transaction transaction) {
        final Boolean isCachedAsValid = _validationCache.isValid(transaction.getHash());
        if (isCachedAsValid != null) { return isCachedAsValid; }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript slpLockingScript = transactionOutput.getLockingScript();

        if (! SlpScriptInflater.matchesSlpFormat(slpLockingScript)) { return null; }

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        final SlpScript slpScript = slpScriptInflater.fromLockingScript(slpLockingScript);
        if (slpScript == null) { return false; }

        switch (slpScript.getType()) {
            case GENESIS: {
                final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) slpScript;
                return _validateSlpGenesisScript(slpGenesisScript);
            }
            case MINT: {
                final SlpMintScript slpMintScript = (SlpMintScript) slpScript;
                return _validateSlpMintTransaction(transaction, slpMintScript, 0);
            }
            case COMMIT: {
                final SlpCommitScript slpCommitScript = (SlpCommitScript) slpScript;
                return _validateSlpCommitScript(slpCommitScript);
            }
            case SEND: {
                final SlpSendScript slpSendScript = (SlpSendScript) slpScript;
                return _validateSlpSendTransaction(transaction, slpSendScript, 0);
            }
            default: {
                return false;
            }
        }
    }

    public void setAllowUnconfirmedTransactions(final Boolean allowUnconfirmedTransactions) {
        _allowUnconfirmedTransactions = allowUnconfirmedTransactions;
    }
}

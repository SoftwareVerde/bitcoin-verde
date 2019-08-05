package com.softwareverde.bitcoin.slp.validator;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
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
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.Map;

public class SlpTransactionValidator {
    public interface TransactionAccumulator {
        Map<Sha256Hash, Transaction> getTransactions(List<Sha256Hash> transactionHashes);
    }

    protected final TransactionAccumulator _transactionAccumulator;

    protected SlpScript _getSlpScript(final Transaction transaction) {
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript slpLockingScript = transactionOutput.getLockingScript();

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        return slpScriptInflater.fromLockingScript(slpLockingScript);
    }

    protected Map<Sha256Hash, Transaction> _getTransactions(final List<TransactionInput> transactionInputs) {
        final ImmutableListBuilder<Sha256Hash> transactionHashes = new ImmutableListBuilder<Sha256Hash>(transactionInputs.getSize());
        for (final TransactionInput transactionInput : transactionInputs) {
            final Sha256Hash transactionHash = transactionInput.getPreviousOutputTransactionHash();
            transactionHashes.add(transactionHash);
        }

        return _transactionAccumulator.getTransactions(transactionHashes.build());
    }

    protected Boolean _validateRecursiveTransactions(final Map<SlpScriptType, ? extends List<Transaction>> recursiveTransactionsToValidate) {
        for (final SlpScriptType slpScriptType : recursiveTransactionsToValidate.keySet()) {
            final List<Transaction> transactions = recursiveTransactionsToValidate.get(slpScriptType);

            switch (slpScriptType) {
                case GENESIS: {
                    for (final Transaction transaction : transactions) {
                        final Boolean isValid = _validateSlpGenesisTransaction(transaction, null);
                        if (! isValid) { return false; }
                    }
                } break;
                case MINT: {
                    for (final Transaction transaction : transactions) {
                        final Boolean isValid = _validateSlpMintTransaction(transaction, null);
                        if (! isValid) { return false; }
                    }
                } break;
                case SEND: {
                    for (final Transaction transaction : transactions) {
                        final Boolean isValid = _validateSlpSendTransaction(transaction, null);
                        if (! isValid) { return false; }
                    }
                } break;
                case COMMIT: {
                    for (final Transaction transaction : transactions) {
                        final Boolean isValid = _validateSlpCommitTransaction(transaction, null);
                        if (! isValid) { return false; }
                    }
                } break;
            }
        }

        return true;
    }

    protected Boolean _validateSlpGenesisTransaction(final Transaction transaction, final SlpGenesisScript nullableSlpGenesisScript) {
        return true;
    }

    protected Boolean _validateSlpCommitTransaction(final Transaction transaction, final SlpCommitScript nullableSlpCommitScript) {
        return true;
    }

    protected Boolean _validateSlpMintTransaction(final Transaction transaction, final SlpMintScript nullableSlpMintScript) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final SlpMintScript slpMintScript = ((nullableSlpMintScript != null) ? nullableSlpMintScript : ((SlpMintScript) _getSlpScript(transaction)));
        final SlpTokenId slpTokenId = slpMintScript.getTokenId();

        final Map<Sha256Hash, Transaction> previousTransactions = _getTransactions(transactionInputs);
        if (previousTransactions == null) { return false; }

        final HashMap<SlpScriptType, MutableList<Transaction>> recursiveTransactionsToValidate = new HashMap<SlpScriptType, MutableList<Transaction>>();

        boolean hasBaton = false;

        for (final TransactionInput transactionInput : transactionInputs) {
            final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

            final Transaction previousTransaction = previousTransactions.get(previousTransactionHash);
            if (previousTransaction == null) { return false; } // TODO: Decide: continue or return false

            final SlpScript previousTransactionSlpScript = _getSlpScript(previousTransaction);
            final boolean isSlpTransaction = (previousTransactionSlpScript != null);
            if (! isSlpTransaction) { continue; }

            final SlpScriptType slpScriptType = previousTransactionSlpScript.getType();
            if (slpScriptType == SlpScriptType.GENESIS) {
                if (! Util.areEqual(slpTokenId, SlpTokenId.wrap(previousTransactionHash))) { continue; }

                final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) previousTransactionSlpScript;
                if (Util.areEqual(previousTransactionOutputIndex, slpGenesisScript.getGeneratorOutputIndex())) {
                    hasBaton = true;
                    ConstUtil.addToListMap(SlpScriptType.GENESIS, previousTransaction, recursiveTransactionsToValidate);
                    break;
                }
            }
            else if (slpScriptType == SlpScriptType.MINT) {
                final SlpMintScript previousSlpMintScript = (SlpMintScript) previousTransactionSlpScript;
                if (! Util.areEqual(slpTokenId, previousSlpMintScript.getTokenId())) { continue; }

                if (Util.areEqual(previousTransactionOutputIndex, previousSlpMintScript.getGeneratorOutputIndex())) {
                    hasBaton = true;
                    ConstUtil.addToListMap(SlpScriptType.MINT, previousTransaction, recursiveTransactionsToValidate);
                    break;
                }
            }
            else if (slpScriptType == SlpScriptType.SEND) {
                // Nothing.
            }
            else if (slpScriptType == SlpScriptType.COMMIT) {
                // Nothing.
            }
        }

        if (! hasBaton) { return false; }

        return _validateRecursiveTransactions(recursiveTransactionsToValidate);
    }

    protected Boolean _validateSlpSendTransaction(final Transaction transaction, final SlpSendScript nullableSlpSendScript) {
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final SlpSendScript slpSendScript = ((nullableSlpSendScript != null) ? nullableSlpSendScript : ((SlpSendScript) _getSlpScript(transaction)));
        final SlpTokenId slpTokenId = slpSendScript.getTokenId();

        final Long totalSendAmount = slpSendScript.getTotalAmount();
        final Map<Sha256Hash, Transaction> previousTransactions = _getTransactions(transactionInputs);
        if (previousTransactions == null) { return false; }

        final HashMap<SlpScriptType, MutableList<Transaction>> recursiveTransactionsToValidate = new HashMap<SlpScriptType, MutableList<Transaction>>();

        long totalSlpAmountReceived = 0L;
        for (final TransactionInput transactionInput : transactionInputs) {
            final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

            final Transaction previousTransaction = previousTransactions.get(previousTransactionHash);
            if (previousTransaction == null) { return false; } // TODO: Decide: continue or return false

            final SlpScript previousTransactionSlpScript = _getSlpScript(previousTransaction);
            final boolean isSlpTransaction = (previousTransactionSlpScript != null);
            if (! isSlpTransaction) { continue; }

            final SlpScriptType slpScriptType = previousTransactionSlpScript.getType();
            if (slpScriptType == SlpScriptType.GENESIS) {
                if (! Util.areEqual(slpTokenId, SlpTokenId.wrap(previousTransactionHash))) { continue; }

                final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) previousTransactionSlpScript;
                if (Util.areEqual(previousTransactionOutputIndex, SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX)) {
                    totalSlpAmountReceived += slpGenesisScript.getTokenCount();
                    ConstUtil.addToListMap(SlpScriptType.GENESIS, previousTransaction, recursiveTransactionsToValidate);
                }
            }
            else if (slpScriptType == SlpScriptType.MINT) {
                final SlpMintScript slpMintScript = (SlpMintScript) previousTransactionSlpScript;
                if (! Util.areEqual(slpTokenId, slpMintScript.getTokenId())) { continue; }

                if (Util.areEqual(previousTransactionOutputIndex, SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX)) {
                    totalSlpAmountReceived += slpMintScript.getTokenCount();
                    ConstUtil.addToListMap(SlpScriptType.MINT, previousTransaction, recursiveTransactionsToValidate);
                }
            }
            else if (slpScriptType == SlpScriptType.SEND) {
                final SlpSendScript previousTransactionSlpSendScript = (SlpSendScript) previousTransactionSlpScript;
                if (! Util.areEqual(slpTokenId, previousTransactionSlpSendScript.getTokenId())) { continue; }

                totalSlpAmountReceived += Util.coalesce(previousTransactionSlpSendScript.getAmount(previousTransactionOutputIndex));
                ConstUtil.addToListMap(SlpScriptType.SEND, previousTransaction, recursiveTransactionsToValidate);
            }
            else if (slpScriptType == SlpScriptType.COMMIT) {
                // Nothing.
            }
        }

        if (totalSlpAmountReceived < totalSendAmount) { return false; }

        return _validateRecursiveTransactions(recursiveTransactionsToValidate);
    }

    public SlpTransactionValidator(final TransactionAccumulator transactionAccumulator) {
        _transactionAccumulator = transactionAccumulator;
    }

    public Boolean validateTransaction(final Transaction transaction) {
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
                return _validateSlpGenesisTransaction(transaction, slpGenesisScript);
            }
            case MINT: {
                final SlpMintScript slpMintScript = (SlpMintScript) slpScript;
                return _validateSlpMintTransaction(transaction, slpMintScript);
            }
            case COMMIT: {
                final SlpCommitScript slpCommitScript = (SlpCommitScript) slpScript;
                return _validateSlpCommitTransaction(transaction, slpCommitScript);
            }
            case SEND: {
                final SlpSendScript slpSendScript = (SlpSendScript) slpScript;
                return _validateSlpSendTransaction(transaction, slpSendScript);
            }
            default: {
                return false;
            }
        }
    }
}

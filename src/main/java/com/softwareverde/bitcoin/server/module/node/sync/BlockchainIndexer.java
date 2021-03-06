package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.commit.SlpCommitScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.util.HashMap;
import java.util.Map;

public class BlockchainIndexer extends SleepyService {
    public static final Integer BATCH_SIZE = 1024;

    protected static class OutputIndexData {
        TransactionId transactionId;
        Integer outputIndex;
        Long amount;
        ScriptType scriptType;
        Address address;
        TransactionId slpTransactionId;
    }

    protected static class InputIndexData {
        TransactionId transactionId;
        Integer inputIndex;
        TransactionOutputId transactionOutputId;
    }

    protected final Integer _threadCount;
    protected final TransactionOutputIndexerContext _context;
    protected final ScriptPatternMatcher _scriptPatternMatcher = new ScriptPatternMatcher();
    protected final SlpScriptInflater _slpScriptInflater = new SlpScriptInflater();

    protected Runnable _onSleepCallback;

    protected TransactionId _getSlpTokenTransactionId(final TransactionId transactionId, final SlpScript slpScript) throws ContextException {
        final SlpTokenId slpTokenId;
        switch (slpScript.getType()) {
            case GENESIS: {
                return transactionId;
            }

            case SEND: {
                final SlpSendScript slpSendScript = (SlpSendScript) slpScript;
                slpTokenId = slpSendScript.getTokenId();
            } break;

            case MINT: {
                final SlpMintScript slpMintScript = (SlpMintScript) slpScript;
                slpTokenId = slpMintScript.getTokenId();
            } break;

            case COMMIT: {
                final SlpCommitScript slpCommitScript = (SlpCommitScript) slpScript;
                slpTokenId = slpCommitScript.getTokenId();
            } break;

            default: {
                slpTokenId = null;
            } break;
        }

        if (slpTokenId == null) { return null; }

        try (final AtomicTransactionOutputIndexerContext context = _context.newTransactionOutputIndexerContext()) {
            return context.getTransactionId(slpTokenId);
        }
    }

    protected List<InputIndexData> _indexTransactionInputs(final AtomicTransactionOutputIndexerContext context, final TransactionId transactionId, final Transaction transaction) throws ContextException {
        final MutableList<InputIndexData> inputIndexDataList = new MutableList<InputIndexData>();

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        final int transactionInputCount = transactionInputs.getCount();
        for (int inputIndex = 0; inputIndex < transactionInputCount; ++inputIndex) {
            final TransactionInput transactionInput = transactionInputs.get(inputIndex);
            final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

            { // Avoid indexing Coinbase Inputs...
                final TransactionOutputIdentifier previousTransactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                if (Util.areEqual(TransactionOutputIdentifier.COINBASE, previousTransactionOutputIdentifier)) {
                    continue;
                }
            }

            final TransactionId previousTransactionId = context.getTransactionId(previousTransactionHash);
            final Transaction previousTransaction = context.getTransaction(previousTransactionId);
            if (previousTransaction == null) {
                Logger.debug("Cannot index input; Transaction does not exist: " + previousTransactionHash);
                continue;
            }

            final TransactionOutputId transactionOutputId = new TransactionOutputId(previousTransactionId, previousTransactionOutputIndex);

            final InputIndexData inputIndexData = new InputIndexData();
            inputIndexData.transactionId = transactionId;
            inputIndexData.inputIndex = inputIndex;
            inputIndexData.transactionOutputId = transactionOutputId;

            inputIndexDataList.add(inputIndexData);
        }

        return inputIndexDataList;
    }

    protected Map<TransactionOutputIdentifier, OutputIndexData> _indexTransactionOutputs(final AtomicTransactionOutputIndexerContext context, final TransactionId transactionId, final Transaction transaction) throws ContextException {
        final HashMap<TransactionOutputIdentifier, OutputIndexData> outputIndexData = new HashMap<TransactionOutputIdentifier, OutputIndexData>();

        final Sha256Hash transactionHash = transaction.getHash();
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final int transactionOutputCount = transactionOutputs.getCount();

        final LockingScript slpLockingScript;
        {
            final TransactionOutput transactionOutput = transactionOutputs.get(0);
            slpLockingScript = transactionOutput.getLockingScript();
        }

        for (int outputIndex = 0; outputIndex < transactionOutputCount; ++outputIndex) {
            final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
            if (! outputIndexData.containsKey(transactionOutputIdentifier)) {

                final Address address;
                {
                    final LockingScript lockingScript = transactionOutput.getLockingScript();
                    final ScriptType scriptType = _scriptPatternMatcher.getScriptType(lockingScript);
                    address = _scriptPatternMatcher.extractAddress(scriptType, lockingScript);
                }

                final OutputIndexData indexData = new OutputIndexData();
                indexData.transactionId = transactionId;
                indexData.outputIndex = outputIndex;
                indexData.amount = transactionOutput.getAmount();
                indexData.scriptType = ScriptType.UNKNOWN;
                indexData.address = address;
                indexData.slpTransactionId = null;

                outputIndexData.put(transactionOutputIdentifier, indexData);
            }
        }

        final ScriptType scriptType = _scriptPatternMatcher.getScriptType(slpLockingScript);
        if (! ScriptType.isSlpScriptType(scriptType)) {
            return outputIndexData;
        }

        boolean slpTransactionIsValid;
        { // Validate SLP Transaction...
            // NOTE: Inflating the whole transaction is mildly costly, but typically this only happens once per SLP transaction, which is required anyway.
            final SlpScript slpScript = _slpScriptInflater.fromLockingScript(slpLockingScript);

            slpTransactionIsValid = ( (slpScript != null) && (transactionOutputCount >= slpScript.getMinimumTransactionOutputCount()) );

            if (slpTransactionIsValid) {
                ScriptType outputScriptType = ScriptType.CUSTOM_SCRIPT;
                final TransactionId slpTokenTransactionId = _getSlpTokenTransactionId(transactionId, slpScript);

                switch (slpScript.getType()) {
                    case GENESIS: {
                        final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) slpScript;
                        final Integer generatorOutputIndex = slpGenesisScript.getBatonOutputIndex();

                        if ( (generatorOutputIndex != null) && (generatorOutputIndex >= transactionOutputCount)) {
                            slpTransactionIsValid = false;
                        }
                        else {
                            outputScriptType = ScriptType.SLP_GENESIS_SCRIPT;

                            { // Mark the Receiving Output as an SLP Output...
                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }

                            if (generatorOutputIndex != null) {
                                // Mark the Mint Baton Output as an SLP Output...
                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, generatorOutputIndex);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }
                        }
                    } break;

                    case MINT: {
                        final SlpMintScript slpMintScript = (SlpMintScript) slpScript;
                        final Integer generatorOutputIndex = slpMintScript.getBatonOutputIndex();

                        if ( (generatorOutputIndex != null) && (generatorOutputIndex >= transactionOutputCount)) {
                            slpTransactionIsValid = false;
                        }
                        else {
                            outputScriptType = ScriptType.SLP_MINT_SCRIPT;

                            { // Mark the Receiving Output as an SLP Output...
                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }

                            if (generatorOutputIndex != null) {
                                // Mark the Mint Baton Output as an SLP Output...
                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, generatorOutputIndex);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }
                        }
                    } break;

                    case SEND: {
                        final SlpSendScript slpSendScript = (SlpSendScript) slpScript;
                        for (int outputIndex = 0; outputIndex < transactionOutputCount; ++outputIndex) {
                            final Long slpAmount = Util.coalesce(slpSendScript.getAmount(outputIndex));

                            if (slpAmount > 0L) {
                                outputScriptType = ScriptType.SLP_SEND_SCRIPT;

                                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                                final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                                indexData.slpTransactionId = slpTokenTransactionId;
                            }
                        }
                    } break;

                    case COMMIT: {
                        outputScriptType = ScriptType.SLP_COMMIT_SCRIPT;
                    } break;
                }

                { // Update the OutputIndexData for the first TransactionOutput...
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, 0);
                    final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                    indexData.scriptType = outputScriptType;

                    if (slpTransactionIsValid) {
                        indexData.slpTransactionId = slpTokenTransactionId;
                    }
                }
            }
        }

        return outputIndexData;
    }

    public BlockchainIndexer(final TransactionOutputIndexerContext context, final Integer threadCount) {
        _context = context;
        _threadCount = threadCount;
    }

    protected TransactionId _indexTransaction(final TransactionId nullableTransactionId, final Transaction nullableTransaction, final AtomicTransactionOutputIndexerContext context) throws ContextException {
        final TransactionId transactionId;
        final Transaction transaction;
        {
            if (nullableTransaction != null) {
                transaction = nullableTransaction;

                if (nullableTransactionId != null) {
                    transactionId = nullableTransactionId;
                }
                else {
                    final Sha256Hash transactionHash = transaction.getHash();
                    transactionId = context.getTransactionId(transactionHash);
                }
            }
            else if (nullableTransactionId != null) {
                transactionId = nullableTransactionId;
                transaction = context.getTransaction(transactionId);
            }
            else { return null; }
        }

        if (transaction == null) {
            Logger.debug("Unable to inflate Transaction for address processing: " + transactionId);
            return null;
        }

        final Map<TransactionOutputIdentifier, OutputIndexData> outputIndexData = _indexTransactionOutputs(context, transactionId, transaction);
        for (final OutputIndexData indexData : outputIndexData.values()) {
            context.indexTransactionOutput(indexData.transactionId, indexData.outputIndex, indexData.amount, indexData.scriptType, indexData.address, indexData.slpTransactionId);
        }

        final List<InputIndexData> inputIndexDataList = _indexTransactionInputs(context, transactionId, transaction);
        for (final InputIndexData inputIndexData : inputIndexDataList) {
            context.indexTransactionInput(inputIndexData.transactionId, inputIndexData.inputIndex, inputIndexData.transactionOutputId);
        }

        return transactionId;
    }

    @Override
    protected void _onStart() {
        Logger.trace("BlockchainIndexer Starting.");
    }

    @Override
    protected Boolean _run() {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final boolean shouldExecuteAsynchronously = (_threadCount > 0);
        final int maxBatchCount = (shouldExecuteAsynchronously ? (BATCH_SIZE * _threadCount) : BATCH_SIZE);
        final MutableList<TransactionId> transactionIdQueue = new MutableList<TransactionId>(maxBatchCount);
        try (final AtomicTransactionOutputIndexerContext context = _context.newTransactionOutputIndexerContext()) {
            final List<TransactionId> queuedTransactionIds = context.getUnprocessedTransactions(maxBatchCount);
            transactionIdQueue.addAll(queuedTransactionIds);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return false;
        }

        if (transactionIdQueue.isEmpty()) {
            Logger.trace("BlockchainIndexer has nothing to do.");
            return false;
        }

        final BatchRunner<TransactionId> batchRunner = new BatchRunner<TransactionId>(BATCH_SIZE, shouldExecuteAsynchronously);
        try {
            batchRunner.run(transactionIdQueue, new BatchRunner.Batch<TransactionId>() {
                @Override
                public void run(final List<TransactionId> transactionIds) throws Exception {
                    try (final AtomicTransactionOutputIndexerContext context = _context.newTransactionOutputIndexerContext()) {
                        context.startDatabaseTransaction();

                        for (final TransactionId transactionId : transactionIds) {
                            _indexTransaction(transactionId, null, context);
                        }

                        context.dequeueTransactionsForProcessing(transactionIds);
                        context.commitDatabaseTransaction();
                    }
                }
            });
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return false;
        }

        nanoTimer.stop();
        final double msElapsed = nanoTimer.getMillisecondsElapsed();
        final int actualBatchCount = transactionIdQueue.getCount();
        final long tps = (long) ((actualBatchCount * 1000L) / (msElapsed > 0D ? msElapsed : 0.01));
        Logger.info("Indexed " + actualBatchCount + " transactions in " + msElapsed + "ms. (" + tps + "tps)");

        return true;
    }

    @Override
    protected void _onSleep() {
        Logger.trace("BlockchainIndexer Sleeping.");
        final Runnable onSleepCallback = _onSleepCallback;
        if (onSleepCallback != null) {
            onSleepCallback.run();
        }
    }

    public void setOnSleepCallback(final Runnable onSleepCallback) {
        _onSleepCallback = onSleepCallback;
    }
}

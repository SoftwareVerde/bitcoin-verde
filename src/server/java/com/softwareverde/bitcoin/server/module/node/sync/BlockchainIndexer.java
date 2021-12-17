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
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptInflater;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.commit.SlpCommitScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.concurrent.service.PausableSleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.DisabledMultiTimer;
import com.softwareverde.util.timer.MultiTimer;
import com.softwareverde.util.timer.NanoTimer;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class BlockchainIndexer extends PausableSleepyService {
    public static final Integer BATCH_SIZE = 1024;

    protected static class OutputIndexData {
        TransactionId transactionId;
        Integer outputIndex;
        Long amount;
        ScriptType scriptType;
        Address address;
        Sha256Hash scriptHash;
        TransactionId slpTransactionId;
        ByteArray memoActionType;
        ByteArray memoActionIdentifier;
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
    protected final MemoScriptInflater _memoScriptInflater = new MemoScriptInflater();

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
        final MutableList<InputIndexData> inputIndexDataList = new MutableList<>();

        final int transactionInputCount;
        final List<Sha256Hash> previousTransactionHashes;
        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        {
            final HashSet<Sha256Hash> previousTransactionHashSet = new HashSet<>();
            transactionInputCount = transactionInputs.getCount();
            for (int inputIndex = 0; inputIndex < transactionInputCount; ++inputIndex) {
                final TransactionInput transactionInput = transactionInputs.get(inputIndex);
                final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

                { // Avoid indexing Coinbase Inputs...
                    final TransactionOutputIdentifier previousTransactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    if (Util.areEqual(TransactionOutputIdentifier.COINBASE, previousTransactionOutputIdentifier)) {
                        continue;
                    }
                }

                previousTransactionHashSet.add(previousTransactionHash);
            }

            final MutableList<Sha256Hash> transactionHashList = new MutableList<>(previousTransactionHashSet);
            transactionHashList.sort(Sha256Hash.COMPARATOR);
            previousTransactionHashes = transactionHashList;
        }

        final Map<Sha256Hash, TransactionId> transactionIds = context.getTransactionIds(previousTransactionHashes);
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

            final TransactionId previousTransactionId = transactionIds.get(previousTransactionHash);
            // final Transaction previousTransaction = context.getTransaction(previousTransactionId);
            if (previousTransactionId == null) {
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

    protected OutputIndexData _indexTransactionOutputWithoutSlpData(final TransactionId transactionId, final Integer outputIndex, final TransactionOutput transactionOutput) {
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final ScriptType scriptType;
        final Address address;
        final Sha256Hash scriptHash;
        {
            scriptType = _scriptPatternMatcher.getScriptType(lockingScript);
            address = _scriptPatternMatcher.extractAddress(scriptType, lockingScript);
            scriptHash = ScriptBuilder.computeScriptHash(lockingScript);
        }

        final ByteArray memoActionType;
        final ByteArray memoActionIdentifier;
        {
            final boolean isMemoScriptType = (scriptType == ScriptType.MEMO_SCRIPT);
            if (isMemoScriptType) {
                // Mark the Receiving Output as a Memo-Action Output.
                // Unlike SLP, any output may be an Memo-Action Output (i.e. not necessarily the first).
                // NOTE: Memo actions are applied to ALL INPUTS of a Transaction, not the Outputs.
                //  Confirmed via: 3971682E94E08AABA660D1D16DDF6F582656FD6BE9C4FA8F360856D982481C39
                final MemoScriptType memoScriptType = MemoScriptInflater.getScriptType(lockingScript);
                memoActionType = (memoScriptType != null ? memoScriptType.getBytes() : null);
            }
            else {
                memoActionType = null;
            }

            if (memoActionType != null) {
                memoActionIdentifier = _memoScriptInflater.getActionIdentifier(lockingScript);
            }
            else { memoActionIdentifier = null; }
        }

        final OutputIndexData indexData = new OutputIndexData();
        indexData.transactionId = transactionId;
        indexData.outputIndex = outputIndex;
        indexData.amount = transactionOutput.getAmount();
        indexData.scriptType = scriptType;
        indexData.address = address;
        indexData.scriptHash = scriptHash;
        indexData.slpTransactionId = null; // Handled later within parent function...
        indexData.memoActionType = memoActionType;
        indexData.memoActionIdentifier = memoActionIdentifier;

        return indexData;
    }

    protected Map<TransactionOutputIdentifier, OutputIndexData> _indexTransactionOutputs(final TransactionId transactionId, final Sha256Hash transactionHash, final List<TransactionOutput> transactionOutputs) throws ContextException {
        final HashMap<TransactionOutputIdentifier, OutputIndexData> outputIndexData = new HashMap<>();

        final int transactionOutputCount = transactionOutputs.getCount();

        final LockingScript firstOutputLockingScript;
        {
            final TransactionOutput transactionOutput = transactionOutputs.get(0);
            firstOutputLockingScript = transactionOutput.getLockingScript();
        }

        for (int outputIndex = 0; outputIndex < transactionOutputCount; ++outputIndex) {
            final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);

            final OutputIndexData indexDataWithoutSlpData = _indexTransactionOutputWithoutSlpData(transactionId, outputIndex, transactionOutput);
            outputIndexData.put(transactionOutputIdentifier, indexDataWithoutSlpData);
        }

        final ScriptType scriptType = _scriptPatternMatcher.getScriptType(firstOutputLockingScript);
        final boolean isSlpScriptType = ScriptType.isSlpScriptType(scriptType);
        if (! isSlpScriptType) {
            return outputIndexData;
        }

        boolean slpFormatIsValid;
        { // Validate SLP Transaction Format...
            // NOTE: Inflating the whole transaction is mildly costly, but typically this only happens once per SLP transaction, which is required anyway.
            final SlpScript slpScript = _slpScriptInflater.fromLockingScript(firstOutputLockingScript);

            slpFormatIsValid = ( (slpScript != null) && (transactionOutputCount >= slpScript.getMinimumTransactionOutputCount()) );

            if (slpFormatIsValid) {
                ScriptType outputScriptType = ScriptType.CUSTOM_SCRIPT;
                final TransactionId slpTokenTransactionId = _getSlpTokenTransactionId(transactionId, slpScript);

                switch (slpScript.getType()) {
                    case GENESIS: {
                        final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) slpScript;
                        final Integer batonOutputIndex = slpGenesisScript.getBatonOutputIndex();

                        outputScriptType = ScriptType.SLP_GENESIS_SCRIPT;

                        { // Mark the Receiving Output as an SLP Output...
                            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                            final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                            indexData.slpTransactionId = slpTokenTransactionId;
                        }

                        if (batonOutputIndex != null && batonOutputIndex < transactionOutputCount) {
                            // Mark the Mint Baton Output as an SLP Output...
                            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, batonOutputIndex);
                            final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                            indexData.slpTransactionId = slpTokenTransactionId;
                        }
                    } break;

                    case MINT: {
                        final SlpMintScript slpMintScript = (SlpMintScript) slpScript;
                        final Integer batonOutputIndex = slpMintScript.getBatonOutputIndex();

                        outputScriptType = ScriptType.SLP_MINT_SCRIPT;

                        { // Mark the Receiving Output as an SLP Output...
                            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                            final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                            indexData.slpTransactionId = slpTokenTransactionId;
                        }

                        if (batonOutputIndex != null && batonOutputIndex < transactionOutputCount) {
                            // Mark the Mint Baton Output as an SLP Output...
                            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, batonOutputIndex);
                            final OutputIndexData indexData = outputIndexData.get(transactionOutputIdentifier);
                            indexData.slpTransactionId = slpTokenTransactionId;
                        }
                    } break;

                    case SEND: {
                        final SlpSendScript slpSendScript = (SlpSendScript) slpScript;
                        for (int outputIndex = 0; outputIndex < transactionOutputCount; ++outputIndex) {
                            final BigInteger slpAmount = slpSendScript.getAmount(outputIndex);

                            if (slpAmount != null && slpAmount.compareTo(BigInteger.ZERO) > 0) {
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
                    indexData.slpTransactionId = slpTokenTransactionId;
                }
            }
        }

        return outputIndexData;
    }

    public BlockchainIndexer(final TransactionOutputIndexerContext context, final Integer threadCount) {
        _context = context;
        _threadCount = threadCount;
    }

    protected TransactionId _indexTransaction(final TransactionId nullableTransactionId, final Transaction nullableTransaction, final AtomicTransactionOutputIndexerContext context, final MultiTimer timer) throws ContextException {
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
                timer.mark("getTransaction");
            }
            else { return null; }
        }

        if (transaction == null) {
            Logger.debug("Unable to inflate Transaction for indexing: " + transactionId);
            return null;
        }

        final Map<TransactionOutputIdentifier, OutputIndexData> outputIndexData;
        {
            final Sha256Hash transactionHash = transaction.getHash();
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            outputIndexData = _indexTransactionOutputs(transactionId, transactionHash, transactionOutputs);
        }
        timer.mark("_indexTransactionOutputs");
        for (final OutputIndexData indexData : outputIndexData.values()) {
            context.indexTransactionOutput(indexData.transactionId, indexData.outputIndex, indexData.amount, indexData.scriptType, indexData.address, indexData.scriptHash, indexData.slpTransactionId, indexData.memoActionType, indexData.memoActionIdentifier);
        }
        timer.mark("indexTransactionOutput");

        final List<InputIndexData> inputIndexDataList = _indexTransactionInputs(context, transactionId, transaction);
        timer.mark("_indexTransactionInputs");
        for (final InputIndexData inputIndexData : inputIndexDataList) {
            context.indexTransactionInput(inputIndexData.transactionId, inputIndexData.inputIndex, inputIndexData.transactionOutputId);
        }
        timer.mark("indexTransactionInput");

        return transactionId;
    }

    @Override
    protected void _onStart() {
        Logger.trace("BlockchainIndexer Starting.");
    }

    @Override
    protected Boolean _execute() {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final boolean shouldExecuteAsynchronously = (_threadCount > 0);
        final int maxBatchCount = (shouldExecuteAsynchronously ? (BATCH_SIZE * _threadCount) : BATCH_SIZE);
        final MutableList<TransactionId> transactionIdQueue = new MutableList<>(maxBatchCount);
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

        final BatchRunner<TransactionId> batchRunner = new BatchRunner<>(BATCH_SIZE, shouldExecuteAsynchronously);
        try {
            final Container<TransactionId> committedTransactionIdContainer = new Container<>();

            batchRunner.run(transactionIdQueue, new BatchRunner.Batch<TransactionId>() {
                @Override
                public void run(final List<TransactionId> transactionIds) throws Exception {
                    final MultiTimer multiTimer = (Logger.isTraceEnabled() ? new MultiTimer() : new DisabledMultiTimer());
                    multiTimer.start();

                    try (final AtomicTransactionOutputIndexerContext context = _context.newTransactionOutputIndexerContext()) {
                        context.initialize();

                        for (final TransactionId transactionId : transactionIds) {
                            _indexTransaction(transactionId, null, context, multiTimer);

                            context.markTransactionProcessed(transactionId);
                            multiTimer.mark("dequeueTransactionsForProcessing");
                        }

                        final TransactionId lastProcessedTransactionId = context.finish();
                        synchronized (committedTransactionIdContainer) {
                            final TransactionId previousValue = committedTransactionIdContainer.value;
                            if ( (previousValue == null) || (previousValue.longValue() < lastProcessedTransactionId.longValue()) ) {
                                committedTransactionIdContainer.value = lastProcessedTransactionId;
                            }
                        }

                        multiTimer.mark("commitDatabaseTransaction");
                    }

                    multiTimer.stop("done");
                    Logger.trace(multiTimer);
                }
            });

            _context.commitLastProcessedTransactionId(committedTransactionIdContainer.value);
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

    public synchronized TransactionId indexUtxosFromUtxoCommitmentImport(final List<TransactionOutputIdentifier> transactionOutputIdentifiers, final List<TransactionOutput> transactionOutputs) throws Exception {
        final int outputCount = transactionOutputs.getCount();
        {
            final int identifiersCount = transactionOutputIdentifiers.getCount();
            if (outputCount != identifiersCount) {
                throw new RuntimeException("Identifier/Output count mismatch. (" + identifiersCount + " != " + outputCount + ")");
            }
        }

        try (final AtomicTransactionOutputIndexerContext context = _context.newTransactionOutputIndexerContext()) {
            context.initialize();

            for (int i = 0; i < outputCount; ++i) {
                final TransactionOutputIdentifier transactionOutputIdentifier = transactionOutputIdentifiers.get(i);
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final TransactionOutput transactionOutput = transactionOutputs.get(i);

                final TransactionId transactionId = context.getTransactionId(transactionHash);

                // NOTE: Normally the whole Transaction's Outputs are provided to _indexTransactionOutputs in order to process SLP,
                //  however, since all SLP Transactions' first output is an OP_RETURN, they will never be included in the UTXO set,
                //  and therefore SLP data cannot be collected.
                final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
                final OutputIndexData outputIndexData = _indexTransactionOutputWithoutSlpData(transactionId, outputIndex, transactionOutput);
                context.indexTransactionOutput(outputIndexData.transactionId, outputIndexData.outputIndex, outputIndexData.amount, outputIndexData.scriptType, outputIndexData.address, outputIndexData.scriptHash, outputIndexData.slpTransactionId, outputIndexData.memoActionType, outputIndexData.memoActionIdentifier);
                context.markTransactionProcessed(transactionId);
            }

            final TransactionId lastFinishedTransactionId = context.finish();

            // NOTE: The TransactionIds from the UTXO commitment are not updated until all of them are completed.
            //  This is done to prevent Transactions from being skipped during a shutdown/restart before UTXO indexing completes.
            // _context.commitLastProcessedTransactionId(lastFinishedTransactionId);

            return lastFinishedTransactionId;
        }
    }

    public void commitLastProcessedTransactionIdFromUtxoCommitmentImport(final TransactionId transactionId) throws Exception {
        _context.commitLastProcessedTransactionId(transactionId);
    }
}

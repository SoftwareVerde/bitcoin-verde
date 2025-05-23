package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.PrototypeDifficulty;
import com.softwareverde.bitcoin.block.validator.thread.ParalleledTaskSpawner;
import com.softwareverde.bitcoin.block.validator.thread.TaskHandler;
import com.softwareverde.bitcoin.block.validator.thread.TaskHandlerFactory;
import com.softwareverde.bitcoin.block.validator.thread.TotalExpenditureTaskHandler;
import com.softwareverde.bitcoin.block.validator.thread.TransactionValidationTaskHandler;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.SpentOutputsTracker;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidationResult;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableArrayListBuilder;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;
import com.softwareverde.util.type.time.SystemTime;

public class BlockValidator {
    public static final Long DO_NOT_TRUST_BLOCKS = -1L;

    protected final UpgradeSchedule _upgradeSchedule;
    protected final Blockchain _blockchain;
    protected final NetworkTime _networkTime;
    protected final BlockHeaderValidator _blockHeaderValidator;

    protected final SystemTime _systemTime = new SystemTime();

    protected Boolean _shouldLogValidBlocks = true;
    protected Integer _maxThreadCount = 4;
    protected Long _trustedBlockHeight = DO_NOT_TRUST_BLOCKS;

    protected BlockValidationResult _validateTransactions(final Block block, final Long blockHeight, final ThreadPool threadPool, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        final MedianBlockTime medianBlockTime = _blockchain.getMedianBlockTime(blockHeight);
        final Thread currentThread = Thread.currentThread();

        { // Enforce max byte count...
            final Integer blockByteCount = block.getByteCount();
            if (blockByteCount > BitcoinConstants.getBlockMaxByteCount()) {
                return BlockValidationResult.invalid("Block exceeded maximum size.");
            }
        }

        final List<Transaction> transactions;
        { // Remove the coinbase transaction and create a lookup map for transaction outputs...
            final List<Transaction> fullTransactionList = block.getTransactions();
            final int transactionCount = (fullTransactionList.getCount() - 1);
            final ImmutableArrayListBuilder<Transaction> listBuilder = new ImmutableArrayListBuilder<>(transactionCount);
            int transactionIndex = 0;
            for (final Transaction transaction : fullTransactionList) {
                if (transactionIndex > 0) {
                    listBuilder.add(transaction);
                }

                transactionIndex += 1;
            }
            transactions = listBuilder.build();
        }

        final BlockOutputs blockOutputs = BlockOutputs.fromBlock(block);

        final int threadCount;
        final boolean executeBothTasksAsynchronously;
        {
            final int transactionCount = transactions.getCount();
            if (transactionCount > 512) {
                executeBothTasksAsynchronously = false;
                threadCount = Math.max(_maxThreadCount, 1);
            }
            else {
                executeBothTasksAsynchronously = true;
                threadCount = Math.max((_maxThreadCount / 2), 1);
            }
        }

        final SpentOutputsTracker spentOutputsTracker = new SpentOutputsTracker(blockOutputs.getOutputCount(), threadCount);
        final ParalleledTaskSpawner<Transaction, TotalExpenditureTaskHandler.ExpenditureResult> totalExpenditureValidationTaskSpawner = new ParalleledTaskSpawner<>("Expenditures", threadPool);
        totalExpenditureValidationTaskSpawner.setTaskHandlerFactory(new TaskHandlerFactory<Transaction, TotalExpenditureTaskHandler.ExpenditureResult>() {
            @Override
            public TaskHandler<Transaction, TotalExpenditureTaskHandler.ExpenditureResult> newInstance() {
                return new TotalExpenditureTaskHandler(unspentTransactionOutputContext, blockOutputs, spentOutputsTracker);
            }
        });

        final TransactionValidator transactionValidator = new TransactionValidatorCore(_upgradeSchedule, _blockchain, _networkTime, unspentTransactionOutputContext, blockOutputs);
        final ParalleledTaskSpawner<Transaction, TransactionValidationTaskHandler.TransactionValidationTaskResult> transactionValidationTaskSpawner = new ParalleledTaskSpawner<>("Validation", threadPool);
        transactionValidationTaskSpawner.setTaskHandlerFactory(new TaskHandlerFactory<Transaction, TransactionValidationTaskHandler.TransactionValidationTaskResult>() {
            @Override
            public TaskHandler<Transaction, TransactionValidationTaskHandler.TransactionValidationTaskResult> newInstance() {
                return new TransactionValidationTaskHandler(blockHeight, transactionValidator);
            }
        });

        if (executeBothTasksAsynchronously) {
            transactionValidationTaskSpawner.executeTasks(transactions, threadCount);
            totalExpenditureValidationTaskSpawner.executeTasks(transactions, threadCount);
        }
        else {
            transactionValidationTaskSpawner.executeTasks(transactions, threadCount);
            transactionValidationTaskSpawner.waitForResults(); // Wait for the results synchronously when the threadCount is one...
            if (currentThread.isInterrupted()) { return BlockValidationResult.invalid("Validation aborted."); } // Bail out if an abort occurred during single-threaded invocation...

            totalExpenditureValidationTaskSpawner.executeTasks(transactions, threadCount);
            totalExpenditureValidationTaskSpawner.waitForResults(); // Wait for the results synchronously when the threadCount is one...
            if (currentThread.isInterrupted()) { return BlockValidationResult.invalid("Validation aborted."); } // Bail out if an abort occurred during single-threaded invocation...
        }

        { // Validate coinbase contains block height...
            if (_upgradeSchedule.isBlockHeightWithinCoinbaseRequired(blockHeight)) {
                final Long blockVersion = block.getVersion();
                if (blockVersion < 2L) {
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return BlockValidationResult.invalid("Invalid block version: " + blockVersion);
                }

                final CoinbaseTransaction coinbaseTransaction = block.getCoinbaseTransaction();
                final UnlockingScript unlockingScript = coinbaseTransaction.getCoinbaseScript();

                final List<Operation> operations = unlockingScript.getOperations();
                final Operation operation = operations.get(0);
                if (operation.getType() != Operation.Type.OP_PUSH) {
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return BlockValidationResult.invalid("Block coinbase does not contain block height.", coinbaseTransaction);
                }

                final PushOperation pushOperation = (PushOperation) operation;
                final Value coinbaseBlockHeightValue = pushOperation.getValue();
                if (! coinbaseBlockHeightValue.isMinimallyEncodedLong()) {
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return BlockValidationResult.invalid("Invalid block height encoding within coinbase.");
                }

                final Long coinbaseBlockHeight = coinbaseBlockHeightValue.asLong();
                if (! Util.areEqual(blockHeight, coinbaseBlockHeight)) {
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return BlockValidationResult.invalid("Invalid block height within coinbase. (found " + coinbaseBlockHeight + ", expected " + blockHeight + ")", coinbaseTransaction);
                }
            }
        }

        { // Validate coinbase input...
            final Transaction coinbaseTransaction = block.getCoinbaseTransaction();
            final List<TransactionInput> transactionInputs = coinbaseTransaction.getTransactionInputs();

            { // Validate transaction amount...
                if (transactionInputs.getCount() != 1) {
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return BlockValidationResult.invalid("Invalid coinbase transaction inputs. Count: " + transactionInputs.getCount() + "; " + "Block: " + block.getHash(), coinbaseTransaction);
                }
            }

            { // Validate previousOutputTransactionHash...
                final TransactionInput transactionInput = transactionInputs.get(0);
                final Sha256Hash previousTransactionOutputHash = transactionInput.getPreviousOutputTransactionHash();
                final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
                final Boolean previousTransactionOutputHashIsValid = Util.areEqual(previousTransactionOutputHash, TransactionOutputIdentifier.COINBASE.getTransactionHash());
                final Boolean previousTransactionOutputIndexIsValid = Util.areEqual(previousTransactionOutputIndex, TransactionOutputIdentifier.COINBASE.getOutputIndex());
                if (! (previousTransactionOutputHashIsValid && previousTransactionOutputIndexIsValid)) {
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return BlockValidationResult.invalid("Invalid coinbase transaction input. " + previousTransactionOutputHash + ":" + previousTransactionOutputIndex + "; " + "Block: " + block.getHash(), coinbaseTransaction);
                }
            }
        }

        final List<TotalExpenditureTaskHandler.ExpenditureResult> expenditureResults = totalExpenditureValidationTaskSpawner.waitForResults();
        if (currentThread.isInterrupted()) {
            // Bail out if an abort occurred...
            transactionValidationTaskSpawner.abort();
            return BlockValidationResult.invalid("Validation aborted."); // Bail out if an abort occurred...
        }
        if (expenditureResults == null) { return BlockValidationResult.invalid("An internal error occurred during ExpenditureValidatorTask."); }

        final List<TransactionValidationTaskHandler.TransactionValidationTaskResult> transactionValidationTaskResults = transactionValidationTaskSpawner.waitForResults();
        if (currentThread.isInterrupted()) { BlockValidationResult.invalid("Validation aborted."); } // Bail out if an abort occurred...
        if (transactionValidationTaskResults == null) { return BlockValidationResult.invalid("An internal error occurred during InputsValidatorTask."); }

        final MutableList<Sha256Hash> invalidTransactions = new MutableArrayList<>();

        final long totalTransactionFees;
        {
            long totalFees = 0L;
            for (final TotalExpenditureTaskHandler.ExpenditureResult expenditureResult : expenditureResults) {
                if (! expenditureResult.isValid) {
                    invalidTransactions.addAll(expenditureResult.invalidTransactions);
                }
                else {
                    totalFees += expenditureResult.totalFees;
                }
            }
            totalTransactionFees = totalFees;
        }
        if (! invalidTransactions.isEmpty()) { return BlockValidationResult.invalid("Invalid transactions expenditures.", invalidTransactions); }

        final int totalSignatureOperationCount;
        {
            final StringBuilder errorMessage = new StringBuilder("Transactions failed to unlock inputs.");
            boolean allTransactionsAreValid = true;
            int signatureOperationCount = 0;
            for (final TransactionValidationTaskHandler.TransactionValidationTaskResult transactionValidationTaskResult : transactionValidationTaskResults) {
                if (transactionValidationTaskResult.isValid()) {
                    signatureOperationCount += transactionValidationTaskResult.getSignatureOperationCount();
                }
                else {
                    allTransactionsAreValid = false;
                    for (final Sha256Hash invalidTransactionHash : transactionValidationTaskResult.getInvalidTransactions()) {
                        invalidTransactions.add(invalidTransactionHash);

                        final TransactionValidationResult transactionValidationResult = transactionValidationTaskResult.getTransactionValidationResult(invalidTransactionHash);
                        errorMessage.append("\n");
                        errorMessage.append(invalidTransactionHash);
                        errorMessage.append(": ");
                        errorMessage.append(transactionValidationResult.errorMessage);
                    }
                }
            }
            totalSignatureOperationCount = signatureOperationCount;

            if (! allTransactionsAreValid) {
                return BlockValidationResult.invalid(errorMessage.toString(), invalidTransactions);
            }
        }

        if (_upgradeSchedule.isSignatureOperationCountingVersionTwoEnabled(medianBlockTime)) { // Enforce maximum Signature operation count...
            // NOTE: Technically, checking the block's maxSigOp count should be checked "live" instead of at the end, but this check is redundant due to
            //  Transactions having a maximum signature operation count, and blocks having a maximum Transaction count.
            final int maximumSignatureOperationCount = (BitcoinConstants.getBlockMaxByteCount() / Block.MIN_BYTES_PER_SIGNATURE_OPERATION);
            Logger.trace("Signature Operations: " + totalSignatureOperationCount + " / " + maximumSignatureOperationCount);
            if (totalSignatureOperationCount > maximumSignatureOperationCount) {
                return BlockValidationResult.invalid("Too many signature operations.");
            }
        }

        { // Validate coinbase amount...
            final Transaction coinbaseTransaction = block.getCoinbaseTransaction();
            final Long maximumCreatedCoinsAmount = BlockHeader.calculateBlockReward(blockHeight);

            final long maximumCoinbaseValue = (maximumCreatedCoinsAmount + totalTransactionFees);

            final long coinbaseTransactionAmount;
            {
                long totalAmount = 0L;
                for (final TransactionOutput transactionOutput : coinbaseTransaction.getTransactionOutputs()) {
                    totalAmount += transactionOutput.getAmount();
                }
                coinbaseTransactionAmount = totalAmount;
            }

            final boolean coinbaseTransactionAmountIsValid = (coinbaseTransactionAmount <= maximumCoinbaseValue);
            if (! coinbaseTransactionAmountIsValid) {
                return BlockValidationResult.invalid("Invalid coinbase transaction amount. Amount: " + coinbaseTransactionAmount + "; " + "Block: "+ block.getHash(), coinbaseTransaction);
            }
        }

        return BlockValidationResult.valid();
    }

    protected BlockValidationResult _validateBlock(final Block block, final Long blockHeight, final Boolean skipHeaderValidation, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        if (! skipHeaderValidation) {
            final BlockHeaderValidator.BlockHeaderValidationResult blockHeaderValidationResult = _blockHeaderValidator.validateBlockHeader(block, blockHeight);
            if (! blockHeaderValidationResult.isValid) {
                return BlockValidationResult.invalid(blockHeaderValidationResult.errorMessage);
            }

            if (! block.isValid()) {
                return BlockValidationResult.invalid("Block header is invalid.");
            }
        }

        { // Ensure the Coinbase Transaction is valid.
            final CoinbaseTransaction coinbaseTransaction = block.getCoinbaseTransaction();
            final Boolean isValidCoinbase = Transaction.isCoinbaseTransaction(coinbaseTransaction);
            if (! isValidCoinbase) {
                return BlockValidationResult.invalid("Coinbase Transaction is invalid.");
            }
        }

        final boolean shouldValidateInputs = (blockHeight > _trustedBlockHeight);
        if (shouldValidateInputs) {
            final NanoTimer validateBlockTimer = new NanoTimer();
            validateBlockTimer.start();

            final Thread currentThread = Thread.currentThread();
            final Integer threadPriority = currentThread.getPriority();
            final CachedThreadPool threadPool = new CachedThreadPool(_maxThreadCount, 60000L, CachedThreadPool.newThreadFactoryWithPriority(threadPriority));
            try {
                threadPool.start();
                final BlockValidationResult transactionsValidationResult = _validateTransactions(block, blockHeight, threadPool, unspentTransactionOutputContext);
                if (! transactionsValidationResult.isValid) { return transactionsValidationResult; }
            }
            finally {
                threadPool.stop();
            }

            validateBlockTimer.stop();
            if (_shouldLogValidBlocks) {
                final List<Transaction> transactions = block.getTransactions();
                Logger.info("Validated " + transactions.getCount() + " transactions in " + (validateBlockTimer.getMillisecondsElapsed()) + "ms (" + ((int) ((transactions.getCount() / validateBlockTimer.getMillisecondsElapsed()) * 1000)) + " tps). " + block.getHash());
            }
        }
        else {
            Logger.debug("Trusting Block Height: " + blockHeight);
        }

        return BlockValidationResult.valid();
    }

    public BlockValidator(final UpgradeSchedule upgradeSchedule, final Blockchain blockchain, final NetworkTime networkTime, final BlockHeaderValidator blockHeaderValidator) {
        _upgradeSchedule = upgradeSchedule;
        _blockchain = blockchain;
        _blockHeaderValidator = blockHeaderValidator;
        _networkTime = networkTime;
    }

    /**
     *  Sets the total number of threads that will be spawned for each call to BlockValidator::Validate.  NOTE: This number should be divisible by 2.
     */
    public void setMaxThreadCount(final Integer maxThreadCount) {
        _maxThreadCount = maxThreadCount;
    }

    public void setTrustedBlockHeight(final Long trustedBlockHeight) {
        _trustedBlockHeight = trustedBlockHeight;
    }

    public BlockValidationResult validateBlock(final Block block, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        final Sha256Hash blockHash = block.getHash();
        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        return _validateBlock(block, blockHeight, false, unspentTransactionOutputContext);
    }

    public BlockValidationResult validateBlock(final Block block, final Long blockHeight, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        return _validateBlock(block, blockHeight, false, unspentTransactionOutputContext);
    }

    /**
     * Validates the provided block for mining.
     *  PrototypeBlock's are valid blocks, with the sole exception of their hash is not required to be valid.
     */
    public BlockValidationResult validatePrototypeBlock(final Block prototypeBlock, final Long blockHeight, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        final MutableBlock mutableBlock = new MutableBlock(prototypeBlock);
        final Difficulty difficulty = prototypeBlock.getDifficulty();
        final PrototypeDifficulty prototypeDifficulty = new PrototypeDifficulty(difficulty);
        mutableBlock.setDifficulty(prototypeDifficulty);
        return _validateBlock(mutableBlock, blockHeight, false, unspentTransactionOutputContext);
    }

    public BlockValidationResult validateBlockTransactions(final Block block, final Long blockHeight, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        return _validateBlock(block, blockHeight, true, unspentTransactionOutputContext);
    }

    public void setShouldLogValidBlocks(final Boolean shouldLogValidBlocks) {
        _shouldLogValidBlocks = shouldLogValidBlocks;
    }
}

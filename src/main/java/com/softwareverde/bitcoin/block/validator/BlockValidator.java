package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.Bip34;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.PrototypeDifficulty;
import com.softwareverde.bitcoin.block.validator.thread.*;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.SpentOutputsTracker;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableArrayListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;
import com.softwareverde.util.type.time.SystemTime;

public class BlockValidator {
    public interface Context extends BlockHeaderValidator.Context, TransactionValidator.Context, TransactionValidatorFactory { }

    public static final Long DO_NOT_TRUST_BLOCKS = -1L;

    protected final Context _context;

    protected final SystemTime _systemTime = new SystemTime();

    protected Boolean _shouldLogValidBlocks = true;
    protected Integer _maxThreadCount = 4;
    protected Long _trustedBlockHeight = DO_NOT_TRUST_BLOCKS;

    protected BlockValidationResult _validateTransactions(final Block block, final Long blockHeight) {
        final Thread currentThread = Thread.currentThread();

        { // Enforce max byte count...
            final Integer blockByteCount = block.getByteCount();
            if (blockByteCount > BlockInflater.MAX_BYTE_COUNT) {
                return BlockValidationResult.invalid("Block exceeded maximum size.");
            }
        }

        final List<Transaction> transactions;
        { // Remove the coinbase transaction and create a lookup map for transaction outputs...
            final List<Transaction> fullTransactionList = block.getTransactions();
            final int transactionCount = (fullTransactionList.getCount() - 1);
            final ImmutableArrayListBuilder<Transaction> listBuilder = new ImmutableArrayListBuilder<Transaction>(transactionCount);
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
        final MainThreadPool threadPool = new MainThreadPool(_maxThreadCount, 1000L);
        threadPool.setThreadPriority(currentThread.getPriority());

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
        final ParalleledTaskSpawner<Transaction, TotalExpenditureTaskHandler.ExpenditureResult> totalExpenditureValidationTaskSpawner = new ParalleledTaskSpawner<Transaction, TotalExpenditureTaskHandler.ExpenditureResult>("Expenditures", threadPool);
        totalExpenditureValidationTaskSpawner.setTaskHandlerFactory(new TaskHandlerFactory<Transaction, TotalExpenditureTaskHandler.ExpenditureResult>() {
            @Override
            public TaskHandler<Transaction, TotalExpenditureTaskHandler.ExpenditureResult> newInstance() {
                return new TotalExpenditureTaskHandler(_context, blockOutputs, spentOutputsTracker);
            }
        });

        final TransactionValidator transactionValidator = _context.getTransactionValidator(blockOutputs, _context);
        final ParalleledTaskSpawner<Transaction, TransactionValidationTaskHandler.TransactionValidationResult> transactionValidationTaskSpawner = new ParalleledTaskSpawner<Transaction, TransactionValidationTaskHandler.TransactionValidationResult>("Validation", threadPool);
        transactionValidationTaskSpawner.setTaskHandlerFactory(new TaskHandlerFactory<Transaction, TransactionValidationTaskHandler.TransactionValidationResult>() {
            @Override
            public TaskHandler<Transaction, TransactionValidationTaskHandler.TransactionValidationResult> newInstance() {
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

        // TODO: Validate max operations per block... (https://bitcoin.stackexchange.com/questions/35691/if-block-sizes-go-up-wont-sigop-limits-have-to-change-too)
        // TODO: Validate transaction does not appear twice within the same Block and Blockchain... (https://github.com/bitcoin/bips/blob/master/bip-0030.mediawiki) (https://github.com/bitcoin/bitcoin/commit/ab91bf39b7c11e9c86bb2043c24f0f377f1cf514)
        // TODO: Create test for PreviousTransactionOutput being EmptyHash/-1 when not coinbase.

        { // Validate coinbase contains block height...
            if (Bip34.isEnabled(blockHeight)) {
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
                final Long coinbaseBlockHeight = pushOperation.getValue().asLong();
                if (blockHeight.longValue() != coinbaseBlockHeight.longValue()) {
                    totalExpenditureValidationTaskSpawner.abort();
                    transactionValidationTaskSpawner.abort();
                    return BlockValidationResult.invalid("Invalid block height within coinbase.", coinbaseTransaction);
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

        final List<TransactionValidationTaskHandler.TransactionValidationResult> unlockedInputsResults = transactionValidationTaskSpawner.waitForResults();
        if (currentThread.isInterrupted()) { BlockValidationResult.invalid("Validation aborted."); } // Bail out if an abort occurred...
        if (unlockedInputsResults == null) { return BlockValidationResult.invalid("An internal error occurred during InputsValidatorTask."); }

        threadPool.stop();

        final MutableList<Sha256Hash> invalidTransactions = new MutableList<Sha256Hash>();

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

        for (final TransactionValidationTaskHandler.TransactionValidationResult transactionValidationResult : unlockedInputsResults) {
            if (! transactionValidationResult.isValid) {
                invalidTransactions.addAll(transactionValidationResult.invalidTransactions);
            }
        }
        if (! invalidTransactions.isEmpty()) { return BlockValidationResult.invalid("Transactions failed to unlock inputs.", invalidTransactions); }

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

    protected BlockValidationResult _validateBlock(final Block block, final Long blockHeight) {
        final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_context);
        final BlockHeaderValidator.BlockHeaderValidationResult blockHeaderValidationResult = blockHeaderValidator.validateBlockHeader(block, blockHeight);
        if (! blockHeaderValidationResult.isValid) {
            return BlockValidationResult.invalid(blockHeaderValidationResult.errorMessage);
        }

        if (! block.isValid()) {
            return BlockValidationResult.invalid("Block header is invalid.");
        }

        final boolean shouldValidateInputs = (blockHeight > _trustedBlockHeight);
        if (shouldValidateInputs) {
            final NanoTimer validateBlockTimer = new NanoTimer();
            validateBlockTimer.start();

            final BlockValidationResult transactionsValidationResult = _validateTransactions(block, blockHeight);
            if (! transactionsValidationResult.isValid) { return transactionsValidationResult; }

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

    public BlockValidator(final Context context) {
        _context = context;
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

    public BlockValidationResult validateBlock(final Block block, final Long blockHeight) {
        return _validateBlock(block, blockHeight);
    }

    /**
     * Validates the provided block for mining.
     *  PrototypeBlock's are valid blocks, with the sole exception of their hash is not required to be valid.
     */
    public BlockValidationResult validatePrototypeBlock(final Block prototypeBlock, final Long blockHeight) {
        final MutableBlock mutableBlock = new MutableBlock(prototypeBlock);
        final Difficulty difficulty = prototypeBlock.getDifficulty();
        final PrototypeDifficulty prototypeDifficulty = new PrototypeDifficulty(difficulty);
        mutableBlock.setDifficulty(prototypeDifficulty);
        return _validateBlock(mutableBlock, blockHeight);
    }

    public BlockValidationResult validateBlockTransactions(final Block block, final Long blockHeight) {
        return _validateBlock(block, blockHeight);
    }

    public void setShouldLogValidBlocks(final Boolean shouldLogValidBlocks) {
        _shouldLogValidBlocks = shouldLogValidBlocks;
    }
}

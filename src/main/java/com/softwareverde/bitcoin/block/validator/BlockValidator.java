package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.bip.Bip34;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.thread.*;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.timer.Timer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.Map;

public class BlockValidator {
    protected static final Integer TOTAL_THREAD_COUNT = 4; // The total number of threads that will be spawned for each call to Validate.  NOTE: This number should be divisible by 2.

    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;
    protected final SystemTime _systemTime = new SystemTime();
    protected final ReadUncommittedDatabaseConnectionFactory _databaseConnectionFactory;
    protected Integer _trustedBlockHeight = 0;

    protected Boolean _validateBlock(final BlockChainSegmentId blockChainSegmentId, final Block block, final Long blockHeight) {
        if (! block.isValid()) {
            Logger.log("Block header is invalid.");
            return false;
        }

        final Timer validateBlockTimer = new Timer();
        validateBlockTimer.start();

        final List<Transaction> transactions;
        final Map<Sha256Hash, Transaction> queuedTransactionOutputs = new HashMap<Sha256Hash, Transaction>();
        { // Remove the coinbase transaction and create a lookup map for transaction outputs...
            final List<Transaction> fullTransactionList = block.getTransactions();
            final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(fullTransactionList.getSize());
            int transactionIndex = 0;
            for (final Transaction transaction : fullTransactionList) {
                if (transactionIndex > 0) {
                    listBuilder.add(transaction);
                }

                queuedTransactionOutputs.put(transaction.getHash(), transaction);

                transactionIndex += 1;
            }
            transactions = listBuilder.build();
        }

        final TaskHandlerFactory<Transaction, Long> totalExpenditureTaskHandlerFactory = new TaskHandlerFactory<Transaction, Long>() {
            @Override
            public TaskHandler<Transaction, Long> newInstance() {
                return new TotalExpenditureTaskHandler(blockChainSegmentId, queuedTransactionOutputs);
            }
        };

        final TaskHandlerFactory<Transaction, Boolean> unlockedInputsTaskHandlerFactory = new TaskHandlerFactory<Transaction, Boolean>() {
            @Override
            public TaskHandler<Transaction, Boolean> newInstance() {
                return new UnlockedInputsTaskHandler(blockChainSegmentId, blockHeight, _networkTime, _medianBlockTime);
            }
        };

        // TODO: Validate block size...
        // TODO: Validate max operations per block... (https://bitcoin.stackexchange.com/questions/35691/if-block-sizes-go-up-wont-sigop-limits-have-to-change-too)

        { // Validate coinbase contains block height...
            if (Bip34.isEnabled(blockHeight)) {
                final Long blockVersion = block.getVersion();
                if (blockVersion < 2L) {
                    Logger.log("Invalid block version.");
                    return false;
                }

                final CoinbaseTransaction coinbaseTransaction = block.getCoinbaseTransaction();
                final UnlockingScript unlockingScript = coinbaseTransaction.getCoinbaseScript();

                final List<Operation> operations = unlockingScript.getOperations();
                final Operation operation = operations.get(0);
                if (operation.getType() != Operation.Type.OP_PUSH) {
                    Logger.log("Block coinbase does not contain block height.");
                    return false;
                }
                final PushOperation pushOperation = (PushOperation) operation;
                final Long coinbaseBlockHeight = pushOperation.getValue().asLong();
                if (blockHeight.longValue() != coinbaseBlockHeight.longValue()) {
                    Logger.log("Invalid block height within coinbase.");
                    return false;
                }
            }
        }

        { // Validate coinbase input...
            final Transaction coinbaseTransaction = block.getCoinbaseTransaction();
            final List<TransactionInput> transactionInputs = coinbaseTransaction.getTransactionInputs();

            { // Validate transaction amount...
                if (transactionInputs.getSize() != 1) {
                    Logger.log("Invalid coinbase transaction inputs. Count: " + transactionInputs.getSize() + "; " + "Block: " + block.getHash());
                    return false;
                }
            }

            { // Validate previousOutputTransactionHash...
                final Sha256Hash requiredHash = new ImmutableSha256Hash();
                final TransactionInput transactionInput = transactionInputs.get(0);
                final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                if (! requiredHash.equals(previousOutputTransactionHash)) {
                    Logger.log("Invalid coinbase transaction input. PreviousTransactionHash: " + previousOutputTransactionHash + "; " + "Block: " + block.getHash());
                    return false;
                }
            }
        }

        final Integer threadCount = (TOTAL_THREAD_COUNT / 2);

        final ParallelledTaskSpawner<Transaction, Long> totalExpenditureValidationTaskSpawner = new ParallelledTaskSpawner<Transaction, Long>(_databaseConnectionFactory);
        totalExpenditureValidationTaskSpawner.setTaskHandlerFactory(totalExpenditureTaskHandlerFactory);
        totalExpenditureValidationTaskSpawner.executeTasks(transactions, threadCount);

        final ParallelledTaskSpawner<Transaction, Boolean> unlockedInputsValidationTaskSpawner = new ParallelledTaskSpawner<Transaction, Boolean>(_databaseConnectionFactory);
        unlockedInputsValidationTaskSpawner.setTaskHandlerFactory(unlockedInputsTaskHandlerFactory);

        final Boolean shouldValidateInputs = (blockHeight > _trustedBlockHeight);
        if (shouldValidateInputs) {
            unlockedInputsValidationTaskSpawner.executeTasks(transactions, threadCount);
        }
        else {
            Logger.log("NOTE: Trusting Block Height: " + blockHeight);
            final List<Transaction> emptyTransactionList = new MutableList<Transaction>();
            unlockedInputsValidationTaskSpawner.executeTasks(emptyTransactionList, threadCount);
        }


        final List<Long> expenditureResults = totalExpenditureValidationTaskSpawner.waitForResults();
        final List<Boolean> unlockedInputsResults = unlockedInputsValidationTaskSpawner.waitForResults();

        if (expenditureResults == null) {
            Logger.log("NOTICE: Expenditure validator returned null...");
            return false;
        }

        if (unlockedInputsResults == null) {
            Logger.log("NOTICE: Inputs validator returned null...");
            return false;
        }

        validateBlockTimer.stop();
        Logger.log("Validated "+ transactions.getSize() + " transactions in " + (validateBlockTimer.getMillisecondsElapsed()) + "ms ("+ ((int) ((transactions.getSize() / validateBlockTimer.getMillisecondsElapsed()) * 1000)) +" tps).");

        final Long totalTransactionFees;
        {
            long amount = 0L;
            for (final Long transactionFeeSubtotal : expenditureResults) {
                if (transactionFeeSubtotal == null) {
                    Logger.log("Block invalid due to invalid expenditures.");
                    return false;
                }
                amount += transactionFeeSubtotal;
            }
            totalTransactionFees = amount;
        }

        for (final Boolean value : unlockedInputsResults) {
            if (! value) {
                Logger.log("Block invalid due to unlocked inputs.");
                return false;
            }
        }

        { // Validate coinbase amount...
            final Transaction coinbaseTransaction = block.getCoinbaseTransaction();
            final Long maximumCreatedCoinsAmount = ((50 * Transaction.SATOSHIS_PER_BITCOIN) >> (blockHeight / 210000));

            final Long maximumCoinbaseValue = (maximumCreatedCoinsAmount + totalTransactionFees);

            final Long coinbaseTransactionAmount;
            {
                long totalAmount = 0L;
                for (final TransactionOutput transactionOutput : coinbaseTransaction.getTransactionOutputs()) {
                    totalAmount += transactionOutput.getAmount();
                }
                coinbaseTransactionAmount = totalAmount;
            }

            final Boolean coinbaseTransactionAmountIsValid = (coinbaseTransactionAmount <= maximumCoinbaseValue);
            if (! coinbaseTransactionAmountIsValid) {
                Logger.log("Invalid coinbase transaction amount. Amount: " + coinbaseTransactionAmount + "; " + "Block: "+ block.getHash());
                return false;
            }
        }

        return true;
    }

    public BlockValidator(final ReadUncommittedDatabaseConnectionFactory threadedConnectionsFactory, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _databaseConnectionFactory = threadedConnectionsFactory;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public void setTrustedBlockHeight(final Integer trustedBlockHeight) {
        _trustedBlockHeight = trustedBlockHeight;
    }

    public Boolean validateBlock(final BlockChainSegmentId blockChainSegmentId, final Block block) {
        final BlockId blockId;
        final Long blockHeight;
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
            blockId = blockDatabaseManager.getBlockIdFromHash(block.getHash());
            blockHeight = blockDatabaseManager.getBlockHeightForBlockId(blockId);

            final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(databaseConnection, _networkTime, _medianBlockTime);
            final Boolean headerIsValid = blockHeaderValidator.validateBlockHeader(blockChainSegmentId, block, blockHeight);
            if (! headerIsValid) { return false; }
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
            return false;
        }

        return _validateBlock(blockChainSegmentId, block, blockHeight);
    }
}

package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.bip.Bip68;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTimeType;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumberType;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.HexUtil;

public class TransactionValidator {
    protected static final Object LOG_INVALID_TRANSACTION_MUTEX = new Object();

    protected final BlockChainDatabaseManager _blockChainDatabaseManager;
    protected final BlockDatabaseManager _blockDatabaseManager;
    protected final TransactionDatabaseManager _transactionDatabaseManager;
    protected final TransactionOutputDatabaseManager _transactionOutputDatabaseManager;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;

    protected Boolean _shouldLogInvalidTransactions = true;

    protected void _logInvalidTransaction(final Transaction transaction, final Context context) {
        if (! _shouldLogInvalidTransactions) { return; }

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();

        final TransactionOutput outputToSpend = context.getTransactionOutput();
        final TransactionInput transactionInput = context.getTransactionInput();

        final LockingScript lockingScript = (outputToSpend != null ? outputToSpend.getLockingScript() : null);
        final UnlockingScript unlockingScript = (transactionInput != null ? transactionInput.getUnlockingScript() : null);

        final Integer transactionInputIndex = context.getTransactionInputIndex();

        synchronized (LOG_INVALID_TRANSACTION_MUTEX) {
            // NOTE: These logging statements are synchronized since Transaction validation is multithreaded, and it is possible to have these log statements intermingle if multiple errors are found...
            Logger.log("\n------------");
            Logger.log("Tx Hash:\t\t" + transaction.getHash() + ((transactionInputIndex != null) ? ("_" + transactionInputIndex) : ("")));
            Logger.log("Tx Bytes:\t\t" + HexUtil.toHexString(transactionDeflater.toBytes(transaction).getBytes()));
            Logger.log("Tx Input:\t\t" + (transactionInput != null ? HexUtil.toHexString(transactionInputDeflater.toBytes(transactionInput)) : null));
            Logger.log("Tx Output:\t\t" + ((outputToSpend != null) ? (outputToSpend.getIndex() + " " + HexUtil.toHexString(transactionOutputDeflater.toBytes(outputToSpend))) : (null)));
            Logger.log("Block Height:\t\t" + context.getBlockHeight());
            Logger.log("Tx Input Index\t\t" + transactionInputIndex);
            Logger.log("Locking Script:\t\t" + lockingScript);
            Logger.log("Unlocking Script:\t" + unlockingScript);
            Logger.log("Median Block Time:\t" + _medianBlockTime.getCurrentTimeInSeconds());
            Logger.log("Network Time:\t\t" + _networkTime.getCurrentTimeInSeconds());
            Logger.log("\n------------\n");
        }
    }

    protected Boolean _shouldValidateLockTime(final Transaction transaction) {
        // If all TransactionInputs' SequenceNumbers are all final (0xFFFFFFFF) then lockTime is disregarded...

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
            if (! sequenceNumber.isDisabled()) {
                return true;
            }
        }

        return false;
    }

    protected TransactionOutput _findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
        final TransactionId transactionId = _transactionDatabaseManager.getTransactionIdFromHash(transactionOutputIdentifier.getBlockChainSegmentId(), transactionOutputIdentifier.getTransactionHash());
        if (transactionId == null) { return null; }

        final TransactionOutputId transactionOutputId = _transactionOutputDatabaseManager.findTransactionOutput(transactionId, transactionOutputIndex);
        if (transactionOutputId == null) { return null; }

        return _transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
    }

    protected Boolean _validateTransactionLockTime(final Context context) {
        final Transaction transaction = context.getTransaction();
        final Long blockHeight = context.getBlockHeight();

        final LockTime transactionLockTime = transaction.getLockTime();
        if (transactionLockTime.getType() == LockTimeType.BLOCK_HEIGHT) {
            if (blockHeight < transactionLockTime.getValue()) { return false; }
        }
        else {
            final Long currentNetworkTime;
            {
                if (Bip113.isEnabled(blockHeight)) {
                    currentNetworkTime = _medianBlockTime.getCurrentTimeInSeconds();
                }
                else {
                    currentNetworkTime = _networkTime.getCurrentTimeInSeconds();
                }
            }

            if (currentNetworkTime < transactionLockTime.getValue()) { return false; }
        }

        return true;
    }

    protected Boolean _validateSequenceNumbers(final BlockChainSegmentId blockChainSegmentId, final Transaction transaction, final Long blockHeight) throws DatabaseException {
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {

            final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
            if (! sequenceNumber.isDisabled()) {

                final BlockId blockIdContainingOutputBeingSpent;
                {
                    final TransactionId transactionId = _transactionDatabaseManager.getTransactionIdFromHash(blockChainSegmentId, transactionInput.getPreviousOutputTransactionHash());
                    if (transactionId == null) { return false; }

                    blockIdContainingOutputBeingSpent = _transactionDatabaseManager.getBlockId(transactionId);
                    if (blockIdContainingOutputBeingSpent == null) { return false; }
                }

                if (sequenceNumber.getType() == SequenceNumberType.SECONDS_ELAPSED) {
                    final Long requiredSecondsElapsed = sequenceNumber.asSecondsElapsed();

                    final MedianBlockTime medianBlockTimeOfOutputBeingSpent = _blockDatabaseManager.calculateMedianBlockTime(blockIdContainingOutputBeingSpent);
                    final Long secondsElapsed = (_medianBlockTime.getCurrentTimeInSeconds() - medianBlockTimeOfOutputBeingSpent.getCurrentTimeInSeconds());

                    final Boolean sequenceNumberIsValid = (secondsElapsed >= requiredSecondsElapsed);
                    if (! sequenceNumberIsValid) {
                        if (_shouldLogInvalidTransactions) {
                            Logger.log("(Elapsed) Sequence Number Invalid: " + secondsElapsed + " < " + requiredSecondsElapsed);
                        }
                        return false;
                    }
                }
                else {
                    final Long blockHeightContainingOutputBeingSpent = _blockDatabaseManager.getBlockHeightForBlockId(blockIdContainingOutputBeingSpent);
                    final Long blockCount = (blockHeight - blockHeightContainingOutputBeingSpent);
                    final Long requiredBlockCount = sequenceNumber.asBlockCount();

                    final Boolean sequenceNumberIsValid = (blockCount >= requiredBlockCount);
                    if (! sequenceNumberIsValid) {
                        if (_shouldLogInvalidTransactions) {
                            Logger.log("(BlockHeight) Sequence Number Invalid: " + blockCount + " < " + requiredBlockCount);
                        }
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public TransactionValidator(final MysqlDatabaseConnection databaseConnection, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        _blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public void setLoggingEnabled(final Boolean shouldLogInvalidTransactions) {
        _shouldLogInvalidTransactions = shouldLogInvalidTransactions;
    }

    public Boolean validateTransactionInputsAreUnlocked(final BlockChainSegmentId blockChainSegmentId, final Long blockHeight, final Transaction transaction) {
        final ScriptRunner scriptRunner = new ScriptRunner();

        final MutableContext context = new MutableContext();
        context.setBlockHeight(blockHeight);

        context.setTransaction(transaction);

        { // Validate nLockTime...
            final Boolean shouldValidateLockTime = _shouldValidateLockTime(transaction);
            if (shouldValidateLockTime) {
                final Boolean lockTimeIsValid = _validateTransactionLockTime(context);
                if (! lockTimeIsValid) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.log("Invalid LockTime for Tx.");
                    }
                    _logInvalidTransaction(transaction, context);
                    return false;
                }
            }
        }

        if (Bip68.isEnabled(blockHeight)) { // Validate Relative SequenceNumber
            if (transaction.getVersion() >= 2L) {
                try {
                    final Boolean sequenceNumbersAreValid = _validateSequenceNumbers(blockChainSegmentId, transaction, blockHeight);
                    if (! sequenceNumbersAreValid) {
                        if (_shouldLogInvalidTransactions) {
                            Logger.log("Transaction SequenceNumber validation failed.");
                        }
                        _logInvalidTransaction(transaction, context);
                        return false;
                    }
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    _logInvalidTransaction(transaction, context);
                    return false;
                }
            }
        }

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);
            final TransactionOutputIdentifier unspentOutputToSpendIdentifier = new TransactionOutputIdentifier(blockChainSegmentId, transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());

            final TransactionOutput outputToSpend;
            try {
                outputToSpend = _findTransactionOutput(unspentOutputToSpendIdentifier);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                _logInvalidTransaction(transaction, context);
                return false;
            }

            if (outputToSpend == null) {
                if (_shouldLogInvalidTransactions) {
                    Logger.log("Transaction " + transaction.getHash() + " references non-existent output: " + transactionInput.getPreviousOutputTransactionHash() + ":" + transactionInput.getPreviousOutputIndex());
                }
                _logInvalidTransaction(transaction, context);
                return false;
            }

            final LockingScript lockingScript = outputToSpend.getLockingScript();
            final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

            context.setTransactionInput(transactionInput);
            context.setTransactionOutputBeingSpent(outputToSpend);
            context.setTransactionInputIndex(i);

            final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);
            if (! inputIsUnlocked) {
                if (_shouldLogInvalidTransactions) {
                    Logger.log("Transaction failed to verify.");
                }
                _logInvalidTransaction(transaction, context);
                return false;
            }
        }

        return true;
    }
}

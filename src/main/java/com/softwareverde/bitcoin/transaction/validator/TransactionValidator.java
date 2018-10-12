package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.bip.Bip68;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.*;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTimeType;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumberType;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.nullable.Nullable;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

public class TransactionValidator {
    protected static final Object LOG_INVALID_TRANSACTION_MUTEX = new Object();

    protected final BlockChainDatabaseManager _blockChainDatabaseManager;
    protected final BlockHeaderDatabaseManager _blockHeaderDatabaseManager;
    protected final TransactionDatabaseManager _transactionDatabaseManager;
    protected final TransactionOutputDatabaseManager _transactionOutputDatabaseManager;
    protected final TransactionInputDatabaseManager _transactionInputDatabaseManager;
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
                    final TransactionId previousOutputTransactionId = _transactionDatabaseManager.getTransactionIdFromHash(transactionInput.getPreviousOutputTransactionHash());
                    if (previousOutputTransactionId == null) { return false; }

                    BlockId parentBlockId = null;
                    // final BlockChainSegmentId blockChainSegmentId = _blockDatabaseManager.getBlockChainSegmentId(blockId);
                    final List<BlockId> previousTransactionBlockIds = _transactionDatabaseManager.getBlockIds(previousOutputTransactionId);
                    for (final BlockId previousTransactionBlockId : previousTransactionBlockIds) {
                        final Boolean isConnected = _blockHeaderDatabaseManager.isBlockConnectedToChain(previousTransactionBlockId, blockChainSegmentId, BlockRelationship.ANCESTOR);
                        if (isConnected) {
                            parentBlockId = previousTransactionBlockId;
                            break;
                        }
                    }
                    if (parentBlockId == null) { return false; }

                    blockIdContainingOutputBeingSpent = parentBlockId;
                }

                if (sequenceNumber.getType() == SequenceNumberType.SECONDS_ELAPSED) {
                    final Long requiredSecondsElapsed = sequenceNumber.asSecondsElapsed();

                    final MedianBlockTime medianBlockTimeOfOutputBeingSpent = _blockHeaderDatabaseManager.calculateMedianBlockTime(blockIdContainingOutputBeingSpent);
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
                    final Long blockHeightContainingOutputBeingSpent = _blockHeaderDatabaseManager.getBlockHeightForBlockId(blockIdContainingOutputBeingSpent);
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

    public TransactionValidator(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection, databaseManagerCache);
        _blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, databaseManagerCache);
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, databaseManagerCache);
        _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection, databaseManagerCache);
        _transactionInputDatabaseManager = new TransactionInputDatabaseManager(databaseConnection, databaseManagerCache);
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public void setLoggingEnabled(final Boolean shouldLogInvalidTransactions) {
        _shouldLogInvalidTransactions = shouldLogInvalidTransactions;
    }

    protected void _logTransactionOutputNotFound(final Sha256Hash transactionHash, final TransactionInput transactionInput, final String extraMessage) {
        Logger.log("Transaction " + transactionHash + " references non-existent output: " + transactionInput.getPreviousOutputTransactionHash() + ":" + transactionInput.getPreviousOutputIndex() + " (" + extraMessage + ")");
    }

    public Boolean validateTransaction(final BlockChainSegmentId blockChainSegmentId, final Long blockHeight, final Transaction transaction) {
        final Sha256Hash transactionHash = transaction.getHash();

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

        final Long totalTransactionInputValue;
        try {
            final TransactionId transactionId = _transactionDatabaseManager.getTransactionIdFromHash(transactionHash);
            if (transactionId == null) {
                Logger.log("Could not find transaction: " + transactionHash);
                return false;
            }

            long totalInputValue = 0L;

            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
            for (int i = 0; i < transactionInputs.getSize(); ++i) {
                final TransactionInput transactionInput = transactionInputs.get(i);


                final Sha256Hash transactionOutputBeingSpentTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                final Integer transactionOutputBeingSpentIndex = transactionInput.getPreviousOutputIndex();

                final TransactionId transactionOutputIdBeingSpentTransactionId = _transactionDatabaseManager.getTransactionIdFromHash(transactionOutputBeingSpentTransactionHash);
                if (transactionOutputIdBeingSpentTransactionId == null) {
                    if (_shouldLogInvalidTransactions) {
                        _logTransactionOutputNotFound(transactionHash, transactionInput, "TransactionId not found.");
                    }
                    return false;
                }

                final TransactionOutputId transactionOutputIdBeingSpent = _transactionOutputDatabaseManager.findTransactionOutput(transactionOutputIdBeingSpentTransactionId, Nullable.wrap(transactionOutputBeingSpentTransactionHash), transactionOutputBeingSpentIndex);
                if (transactionOutputIdBeingSpent == null) {
                    if (_shouldLogInvalidTransactions) {
                        _logTransactionOutputNotFound(transactionHash, transactionInput, "TransactionOutputId not found.");
                    }
                    return false;
                }

                { // Validate TransactionOutput exists on blockChainSegmentId...
                    final BlockId blockIdContainingTransactionOutputBeingSpent = _transactionDatabaseManager.getBlockId(blockChainSegmentId, transactionOutputIdBeingSpentTransactionId);
                    if (blockIdContainingTransactionOutputBeingSpent == null) {
                        if (_shouldLogInvalidTransactions) {
                            _logTransactionOutputNotFound(transactionHash, transactionInput, "TransactionOutput does not exist on BlockChainSegmentId: " + blockChainSegmentId);
                        }
                        return false;
                    }
                }

                { // Validate TransactionOutput hasn't already been spent...
                    final List<TransactionInputId> spendingTransactionInputIds = _transactionInputDatabaseManager.getTransactionInputIdsSpendingTransactionOutput(transactionOutputIdBeingSpent);
                    for (final TransactionInputId spendingTransactionInputId : spendingTransactionInputIds) {
                        final TransactionId spendingTransactionInputIdTransactionId = _transactionInputDatabaseManager.getTransactionId(spendingTransactionInputId);
                        if (! Util.areEqual(spendingTransactionInputIdTransactionId, transactionId)) {
                            if (_shouldLogInvalidTransactions) {
                                Logger.log("Transaction " + transactionHash + " spends already-spent output: " + transactionInput.getPreviousOutputTransactionHash() + ":" + transactionInput.getPreviousOutputIndex());
                            }
                            return false;
                        }
                    }
                }

                final TransactionOutput transactionOutputBeingSpent = _transactionOutputDatabaseManager.getTransactionOutput(transactionOutputIdBeingSpent);

                totalInputValue += transactionOutputBeingSpent.getAmount();

                final LockingScript lockingScript = transactionOutputBeingSpent.getLockingScript();
                final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

                context.setTransactionInput(transactionInput);
                context.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
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

            totalTransactionInputValue = totalInputValue;
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }

        { // Validate that the total input value is greater than or equal to the output value...
            final Long totalTransactionOutputValue;
            {
                long totalOutputValue = 0L;
                for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                    totalOutputValue += transactionOutput.getAmount();
                }
                totalTransactionOutputValue = totalOutputValue;
            }

            if (totalTransactionInputValue < totalTransactionOutputValue) {
                Logger.log("Total TransactionInput value is less than the TransactionOutput value. (" + totalTransactionInputValue + " < " + totalTransactionOutputValue + ")");
                return false;
            }
        }

        return true;
    }
}

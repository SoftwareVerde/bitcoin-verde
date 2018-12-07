package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.bip.Bip68;
import com.softwareverde.bitcoin.bip.HF20181115;
import com.softwareverde.bitcoin.bip.HF20181115SV;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
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
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
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
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

public class TransactionValidator {
    public static final Long COINBASE_MATURITY = 100L;

    protected static final Object LOG_INVALID_TRANSACTION_MUTEX = new Object();

    protected final BlockchainDatabaseManager _blockchainDatabaseManager;
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
            Logger.log("Tx Input:\t\t" + (transactionInput != null ? transactionInputDeflater.toBytes(transactionInput) : null));
            Logger.log("Tx Output:\t\t" + ((outputToSpend != null) ? (outputToSpend.getIndex() + " " + transactionOutputDeflater.toBytes(outputToSpend)) : (null)));
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

    protected Boolean _validateSequenceNumbers(final BlockchainSegmentId blockchainSegmentId, final Transaction transaction, final Long blockHeight) throws DatabaseException {
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {

            final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
            if (! sequenceNumber.isDisabled()) {

                final BlockId blockIdContainingOutputBeingSpent;
                {
                    final TransactionId previousOutputTransactionId = _transactionDatabaseManager.getTransactionId(transactionInput.getPreviousOutputTransactionHash());
                    if (previousOutputTransactionId == null) { return false; }

                    BlockId parentBlockId = null;
                    // final BlockchainSegmentId blockchainSegmentId = _blockDatabaseManager.getBlockchainSegmentId(blockId);
                    final List<BlockId> previousTransactionBlockIds = _transactionDatabaseManager.getBlockIds(previousOutputTransactionId);
                    for (final BlockId previousTransactionBlockId : previousTransactionBlockIds) {
                        final Boolean isConnected = _blockHeaderDatabaseManager.isBlockConnectedToChain(previousTransactionBlockId, blockchainSegmentId, BlockRelationship.ANCESTOR);
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
                    final Long blockHeightContainingOutputBeingSpent = _blockHeaderDatabaseManager.getBlockHeight(blockIdContainingOutputBeingSpent);
                    final Long blockCount = (blockHeight - blockHeightContainingOutputBeingSpent);
                    final Long requiredBlockCount = sequenceNumber.asBlockCount();

                    final Boolean sequenceNumberIsValid = (blockCount >= requiredBlockCount);
                    if (! sequenceNumberIsValid) {
                        if (_shouldLogInvalidTransactions) {
                            Logger.log("(BlockHeight) Sequence Number Invalid: " + blockCount + " >= " + requiredBlockCount);
                        }
                        return false;
                    }
                }
            }
        }

        return true;
    }

    protected Integer _getOutputSpendCount(final BlockchainSegmentId blockchainSegmentId, final TransactionOutputId transactionOutputId, final Long blockHeight, final Boolean includeMemoryPoolTransactions) throws DatabaseException {
        int spendCount = 0;
        final List<TransactionInputId> spendingTransactionInputIds = _transactionInputDatabaseManager.getTransactionInputIdsSpendingTransactionOutput(transactionOutputId);
        for (final TransactionInputId spendingTransactionInputId : spendingTransactionInputIds) {
            final TransactionId spendingTransactionInputIdTransactionId = _transactionInputDatabaseManager.getTransactionId(spendingTransactionInputId);

            if (includeMemoryPoolTransactions) {
                final Boolean transactionIsInMemoryPool = _transactionDatabaseManager.isUnconfirmedTransaction(spendingTransactionInputIdTransactionId);
                if (transactionIsInMemoryPool) {
                    spendCount += 1;
                }
            }

            final List<BlockId> blocksSpendingOutput = _transactionDatabaseManager.getBlockIds(spendingTransactionInputIdTransactionId);

            for (final BlockId blockId : blocksSpendingOutput) {
                final Long blockIdBlockHeight = _blockHeaderDatabaseManager.getBlockHeight(blockId);
                if (Util.areEqual(blockHeight, blockIdBlockHeight)) { continue; }

                final Boolean blockIsConnectedToThisChain = _blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANCESTOR);
                if (blockIsConnectedToThisChain) {
                    spendCount += 1;
                }
            }
        }
        return spendCount;
    }

    protected Integer _getOutputMinedCount(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionOutputTransactionId, final Boolean includeMemoryPool) throws DatabaseException {
        final NanoTimer getBlockIdsTimer = new NanoTimer();
        final NanoTimer isConnectedToChainTimer = new NanoTimer();

        int minedCount = 0;
        getBlockIdsTimer.start();
        final List<BlockId> blockIdsMiningTransactionOutputBeingSpent = _transactionDatabaseManager.getBlockIds(transactionOutputTransactionId);
        getBlockIdsTimer.stop();
        for (final BlockId blockId : blockIdsMiningTransactionOutputBeingSpent) {
            isConnectedToChainTimer.start();
            final Boolean blockIsConnectedToThisChain = _blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANCESTOR);
            isConnectedToChainTimer.stop();
            if (blockIsConnectedToThisChain) {
                minedCount += 1;
            }
        }

        if (includeMemoryPool) {
            final Boolean transactionOutputIsInMemoryPool = _transactionDatabaseManager.isUnconfirmedTransaction(transactionOutputTransactionId);
            if (transactionOutputIsInMemoryPool) {
                minedCount += 1;
            }
        }

        return minedCount;
    }

    public TransactionValidator(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, databaseManagerCache);
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

    public Boolean validateTransaction(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight, final Transaction transaction, final Boolean validateForMemoryPool) {
        final Sha256Hash transactionHash = transaction.getHash();

        final ScriptRunner scriptRunner = new ScriptRunner();

        final MutableContext context = new MutableContext();
        context.setBlockHeight(blockHeight);

        context.setTransaction(transaction);

        { // Validate Transaction Byte Count...
            if ( (HF20181115.isEnabled(blockHeight)) && (! HF20181115SV.isEnabled(blockHeight)) ) {
                final TransactionDeflater transactionDeflater = new TransactionDeflater();
                final Integer transactionByteCount = transactionDeflater.getByteCount(transaction);
                if (transactionByteCount < 100) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.log("Invalid Transaction Byte Count: " + transactionByteCount + " " + transactionHash);
                    }
                    return false;
                }
            }
        }

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
                    final Boolean sequenceNumbersAreValid = _validateSequenceNumbers(blockchainSegmentId, transaction, blockHeight);
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

        // TODO: Consider enforcing Canonical Transaction Order (HF20181115)...

        final Long totalTransactionInputValue;
        try {
            final TransactionId transactionId = _transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId == null) {
                Logger.log("Could not find transaction: " + transactionHash);
                return false;
            }

            long totalInputValue = 0L;

            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

            if (transactionInputs.isEmpty()) {
                if (_shouldLogInvalidTransactions) {
                    Logger.log("Invalid Transaction (No Inputs) " + transactionHash);
                }
                return false;
            }

            for (int i = 0; i < transactionInputs.getSize(); ++i) {
                final TransactionInput transactionInput = transactionInputs.get(i);

                final Sha256Hash transactionOutputBeingSpentTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                final TransactionId transactionOutputBeingSpentTransactionId = _transactionDatabaseManager.getTransactionId(transactionOutputBeingSpentTransactionHash);
                if (transactionOutputBeingSpentTransactionId == null) {
                    if (_shouldLogInvalidTransactions) {
                        _logTransactionOutputNotFound(transactionHash, transactionInput, "TransactionId not found.");
                    }
                    return false;
                }

                { // Enforcing Coinbase Maturity... (If the input is a coinbase then the coinbase must be at least 100 blocks old.)
                    final Boolean transactionOutputBeingSpentIsCoinbaseTransaction = (Util.areEqual(Sha256Hash.EMPTY_HASH, transactionInput.getPreviousOutputTransactionHash()));
                    if (transactionOutputBeingSpentIsCoinbaseTransaction) {
                        final BlockId transactionOutputBeingSpentBlockId = _transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionOutputBeingSpentTransactionId);
                        final Long blockHeightOfTransactionOutputBeingSpent = _blockHeaderDatabaseManager.getBlockHeight(transactionOutputBeingSpentBlockId);
                        final Long coinbaseMaturity = (blockHeight - blockHeightOfTransactionOutputBeingSpent);
                        if (coinbaseMaturity <= COINBASE_MATURITY) {
                            if (_shouldLogInvalidTransactions) {
                                Logger.log("Invalid Transaction. Attempted to spend coinbase before maturity." + transactionHash);
                            }
                            return false;
                        }
                    }
                }

                final TransactionOutputId transactionOutputIdBeingSpent = _transactionOutputDatabaseManager.findTransactionOutput(TransactionOutputIdentifier.fromTransactionInput(transactionInput));
                if (transactionOutputIdBeingSpent == null) {
                    if (_shouldLogInvalidTransactions) {
                        _logTransactionOutputNotFound(transactionHash, transactionInput, "TransactionOutputId not found.");
                    }
                    return false;
                }

                final Integer outputBeingSpentMinedCount = _getOutputMinedCount(blockchainSegmentId, transactionOutputBeingSpentTransactionId, validateForMemoryPool);

                { // Validate the UTXO has been mined on this blockchain...
                    if (outputBeingSpentMinedCount == 0) {
                        if (_shouldLogInvalidTransactions) {
                            _logTransactionOutputNotFound(transactionHash, transactionInput, "TransactionOutput does not exist on BlockchainSegmentId: " + blockchainSegmentId);
                        }
                        return false;
                    }
                }

                final Integer outputBeingSpentSpendCount = _getOutputSpendCount(blockchainSegmentId, transactionOutputIdBeingSpent, blockHeight, validateForMemoryPool);

                { // Validate TransactionOutput hasn't already been spent...
                    // TODO: The logic currently implemented would allow for duplicate transactions to be spent (which is partially against BIP30 and is definitely counter to how the reference client handles it).  What consensus considers "correct" is that the first duplicate becomes unspendable.
                    if (outputBeingSpentSpendCount >= outputBeingSpentMinedCount) {
                        if (_shouldLogInvalidTransactions) {
                            Logger.log("Transaction " + transactionHash + " spends already-spent output: " + transactionInput.getPreviousOutputTransactionHash() + ":" + transactionInput.getPreviousOutputIndex() + " Mined Count: " + outputBeingSpentMinedCount + " | Spend Count: " + outputBeingSpentSpendCount);
                        }
                        return false;
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
                        Logger.log("Transaction failed to verify: " + transactionHash);
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

                    // TODO: Validate that the output indices are sequential and start at 0... (Must check reference client if it does the same.)
                    // TODO: Validate the output count is not zero...
                    // TODO: Validate the output value isn't negative... (Unknown if zero is allowed.)
                }
                totalTransactionOutputValue = totalOutputValue;
            }

            if (totalTransactionInputValue < totalTransactionOutputValue) {
                Logger.log("Total TransactionInput value is less than the TransactionOutput value. (" + totalTransactionInputValue + " < " + totalTransactionOutputValue + ") Tx: " + transactionHash);
                return false;
            }
        }

        return true;
    }
}

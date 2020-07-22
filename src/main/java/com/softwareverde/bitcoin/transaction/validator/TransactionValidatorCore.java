package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.bip.Bip68;
import com.softwareverde.bitcoin.bip.HF20181115;
import com.softwareverde.bitcoin.bip.HF20181115SV;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTimeType;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumberType;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.runner.context.TransactionContext;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.HexUtil;

import java.util.HashSet;

public class TransactionValidatorCore implements TransactionValidator {
    protected static final Object LOG_INVALID_TRANSACTION_MUTEX = new Object();

    protected final Context _context;
    protected final BlockOutputs _blockOutputs;

    protected Boolean _shouldLogInvalidTransactions = true;

    protected Long _getCoinbaseMaturity() {
        return TransactionValidator.COINBASE_MATURITY;
    }

    protected void _logInvalidTransaction(final Long blockHeight, final Transaction transaction, final TransactionContext transactionContext) {
        if (! _shouldLogInvalidTransactions) { return; }

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();

        final TransactionOutput outputToSpend = transactionContext.getTransactionOutput();
        final TransactionInput transactionInput = transactionContext.getTransactionInput();

        final LockingScript lockingScript = (outputToSpend != null ? outputToSpend.getLockingScript() : null);
        final UnlockingScript unlockingScript = (transactionInput != null ? transactionInput.getUnlockingScript() : null);

        final Integer transactionInputIndex = transactionContext.getTransactionInputIndex();

        synchronized (LOG_INVALID_TRANSACTION_MUTEX) {
            final MedianBlockTime medianBlockTime = transactionContext.getMedianBlockTime();
            final NetworkTime networkTime = _context.getNetworkTime();

            // NOTE: These logging statements are synchronized since Transaction validation is multithreaded, and it is possible to have these log statements intermingle if multiple errors are found...
            Logger.debug("\n------------");
            Logger.debug("Tx Hash:\t\t\t" + transaction.getHash() + ((transactionInputIndex != null) ? ("_" + transactionInputIndex) : ("")));
            Logger.debug("Tx Bytes:\t\t\t" + HexUtil.toHexString(transactionDeflater.toBytes(transaction).getBytes()));
            Logger.debug("Tx Input:\t\t\t" + (transactionInput != null ? transactionInputDeflater.toBytes(transactionInput) : null));
            Logger.debug("Tx Output:\t\t\t" + ((outputToSpend != null) ? (outputToSpend.getIndex() + " " + transactionOutputDeflater.toBytes(outputToSpend)) : (null)));
            Logger.debug("Block Height:\t\t" + transactionContext.getBlockHeight());
            Logger.debug("Tx Input Index\t\t" + transactionInputIndex);
            Logger.debug("Locking Script:\t" + lockingScript);
            Logger.debug("Unlocking Script:\t" + unlockingScript);
            Logger.debug("Median Block Time:\t" + medianBlockTime.getCurrentTimeInSeconds());
            Logger.debug("Network Time:\t\t" + networkTime.getCurrentTimeInSeconds());
            Logger.debug("\n------------\n");
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

    protected Boolean _validateTransactionLockTime(final TransactionContext transactionContext) {
        final Transaction transaction = transactionContext.getTransaction();
        final Long blockHeight = transactionContext.getBlockHeight();

        final LockTime transactionLockTime = transaction.getLockTime();
        if (transactionLockTime.getType() == LockTimeType.BLOCK_HEIGHT) {
            if (blockHeight < transactionLockTime.getValue()) { return false; }
        }
        else {
            final Long currentNetworkTime;
            {
                if (Bip113.isEnabled(blockHeight)) {
                    final MedianBlockTime medianBlockTime = transactionContext.getMedianBlockTime();
                    currentNetworkTime = medianBlockTime.getCurrentTimeInSeconds();
                }
                else {
                    final NetworkTime networkTime = _context.getNetworkTime();
                    currentNetworkTime = networkTime.getCurrentTimeInSeconds();
                }
            }

            if (currentNetworkTime < transactionLockTime.getValue()) { return false; }
        }

        return true;
    }

    protected TransactionOutput _getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        {
            final TransactionOutput transactionOutput = _context.getTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) { return transactionOutput; }
        }

        if (_blockOutputs != null) {
            final TransactionOutput transactionOutput = _blockOutputs.getTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) { return transactionOutput; }
        }

        return null;
    }

    protected Long _getTransactionOutputBlockHeight(final TransactionOutputIdentifier transactionOutputIdentifier, final Long blockHeight) {
        {
            final Long transactionOutputBlockHeight = _context.getBlockHeight(transactionOutputIdentifier);
            if (transactionOutputBlockHeight != null) { return transactionOutputBlockHeight; }
        }

        if (_blockOutputs != null) {
            final TransactionOutput transactionOutput = _blockOutputs.getTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) { return blockHeight; }
        }

        return null;
    }

    /**
     * Returns the MedianBlockTime of the TransactionOutput specified by `transactionOutputIdentifier`.
     *  NOTE: The MedianBlockTime used for the TransactionOutput is the parent of the block that mined it, as per BIP-68.
     *      "The mining date of the output is equal to the median-time-past of the previous block which mined it."
     */
    protected MedianBlockTime _getTransactionOutputMedianBlockTime(final TransactionOutputIdentifier transactionOutputIdentifier, final Long blockHeight) {
        {
            final Long transactionOutputBlockHeight = _context.getBlockHeight(transactionOutputIdentifier);
            final MedianBlockTime transactionOutputMedianBlockTime = _context.getMedianBlockTime(transactionOutputBlockHeight - 1L);
            if (transactionOutputMedianBlockTime != null) { return transactionOutputMedianBlockTime; }
        }

        if (_blockOutputs != null) {
            final TransactionOutput transactionOutput = _blockOutputs.getTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) {
                final MedianBlockTime transactionOutputMedianBlockTime = _context.getMedianBlockTime(blockHeight - 1L);
                return transactionOutputMedianBlockTime;
            }
        }

        return null;
    }

    protected Boolean _isTransactionOutputCoinbase(final TransactionOutputIdentifier transactionOutputIdentifier) {
        {
            final Boolean transactionOutputIsCoinbase = _context.isCoinbaseTransactionOutput(transactionOutputIdentifier);
            if (transactionOutputIsCoinbase != null) { return transactionOutputIsCoinbase; }
        }

        if (_blockOutputs != null) {
            final Boolean isCoinbaseTransactionOutput = _blockOutputs.isCoinbaseTransactionOutput(transactionOutputIdentifier);
            if (isCoinbaseTransactionOutput != null) { return isCoinbaseTransactionOutput; }
        }

        return null;
    }

    protected Boolean _validateSequenceNumbers(final Transaction transaction, final Long blockHeight) {
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
            if (sequenceNumber.isDisabled()) { continue; }

            final TransactionOutputIdentifier previousTransactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);

            if (sequenceNumber.getType() == SequenceNumberType.SECONDS_ELAPSED) {
                final Long requiredSecondsElapsed = sequenceNumber.asSecondsElapsed();

                final Long previousBlockHeight = (blockHeight - 1L);
                final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(previousBlockHeight);
                final MedianBlockTime medianBlockTimeOfOutputBeingSpent = _getTransactionOutputMedianBlockTime(previousTransactionOutputIdentifier, blockHeight);
                final long secondsElapsed = (medianBlockTime.getCurrentTimeInSeconds() - medianBlockTimeOfOutputBeingSpent.getCurrentTimeInSeconds());

                final boolean sequenceNumberIsValid = (secondsElapsed >= requiredSecondsElapsed);
                if (! sequenceNumberIsValid) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("(Elapsed) Sequence Number Invalid: " + secondsElapsed + " < " + requiredSecondsElapsed);
                    }
                    return false;
                }
            }
            else {
                final Long blockHeightContainingOutputBeingSpent = _getTransactionOutputBlockHeight(previousTransactionOutputIdentifier, blockHeight);
                final long blockCount = (blockHeight - blockHeightContainingOutputBeingSpent);
                final Long requiredBlockCount = sequenceNumber.asBlockCount();

                final boolean sequenceNumberIsValid = (blockCount >= requiredBlockCount);
                if (! sequenceNumberIsValid) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("(BlockHeight) Sequence Number Invalid: " + blockCount + " >= " + requiredBlockCount);
                    }
                    return false;
                }
            }
        }

        return true;
    }

    public TransactionValidatorCore(final Context context) {
        this(null, context);
    }

    public TransactionValidatorCore(final BlockOutputs blockOutputs, final Context context) {
        _context = context;
        _blockOutputs = blockOutputs;
    }

    @Override
    public void setLoggingEnabled(final Boolean shouldLogInvalidTransactions) {
        _shouldLogInvalidTransactions = shouldLogInvalidTransactions;
    }

    @Override
    public Boolean validateTransaction(final Long blockHeight, final Transaction transaction) {
        final Sha256Hash transactionHash = transaction.getHash();

        final ScriptRunner scriptRunner = new ScriptRunner();

        final Long previousBlockHeight = (blockHeight - 1L);
        final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(previousBlockHeight);

        final MutableTransactionContext transactionContext = new MutableTransactionContext();
        transactionContext.setBlockHeight(blockHeight);
        transactionContext.setMedianBlockTime(medianBlockTime);

        transactionContext.setTransaction(transaction);

        { // Validate Transaction Byte Count...
            if ( (HF20181115.isEnabled(blockHeight)) && (! HF20181115SV.isEnabled(blockHeight)) ) {
                final TransactionDeflater transactionDeflater = new TransactionDeflater();
                final Integer transactionByteCount = transactionDeflater.getByteCount(transaction);
                if (transactionByteCount < 100) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Invalid Transaction Byte Count: " + transactionByteCount + " " + transactionHash);
                    }
                    return false;
                }
            }
        }

        { // Validate nLockTime...
            final Boolean shouldValidateLockTime = _shouldValidateLockTime(transaction);
            if (shouldValidateLockTime) {
                final Boolean lockTimeIsValid = _validateTransactionLockTime(transactionContext);
                if (! lockTimeIsValid) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Invalid LockTime for Tx.");
                    }
                    _logInvalidTransaction(blockHeight, transaction, transactionContext);
                    return false;
                }
            }
        }

        if (Bip68.isEnabled(blockHeight)) { // Validate Relative SequenceNumber
            if (transaction.getVersion() >= 2L) {
                final Boolean sequenceNumbersAreValid = _validateSequenceNumbers(transaction, blockHeight);
                if (! sequenceNumbersAreValid) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Transaction SequenceNumber validation failed.");
                    }
                    _logInvalidTransaction(blockHeight, transaction, transactionContext);
                    return false;
                }
            }
        }

        final long totalTransactionInputValue;
        {
            long totalInputValue = 0L;

            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

            if (transactionInputs.isEmpty()) {
                if (_shouldLogInvalidTransactions) {
                    Logger.debug("Invalid Transaction (No Inputs) " + transactionHash);
                }
                return false;
            }

            final int transactionInputCount = transactionInputs.getCount();
            final HashSet<TransactionOutputIdentifier> spentOutputIdentifiers = new HashSet<TransactionOutputIdentifier>(transactionInputCount);

            for (int i = 0; i < transactionInputCount; ++i) {
                final TransactionInput transactionInput = transactionInputs.get(i);
                final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                final boolean previousOutputIsUniqueToTransaction = spentOutputIdentifiers.add(transactionOutputIdentifierBeingSpent);
                if (! previousOutputIsUniqueToTransaction) { // The transaction attempted to spend the same previous output twice...
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Invalid Transaction. Transaction spends same previous output twice. " + transactionHash);
                    }
                    return false;
                }

                { // Enforcing Coinbase Maturity... (If the input is a coinbase then the coinbase must be at least 100 blocks old.)
                    final Boolean transactionOutputBeingSpentIsCoinbaseTransaction = _isTransactionOutputCoinbase(transactionOutputIdentifierBeingSpent);
                    if (transactionOutputBeingSpentIsCoinbaseTransaction == null) {
                        if (_shouldLogInvalidTransactions) {
                            Logger.debug("Invalid Transaction.  Previous output does not exist. " + transactionOutputIdentifierBeingSpent);
                        }
                        return false;
                    }

                    if (transactionOutputBeingSpentIsCoinbaseTransaction) {
                        final Long blockHeightOfTransactionOutputBeingSpent = _getTransactionOutputBlockHeight(transactionOutputIdentifierBeingSpent, blockHeight);
                        final long coinbaseMaturity = (blockHeight - blockHeightOfTransactionOutputBeingSpent);
                        final Long requiredCoinbaseMaturity = _getCoinbaseMaturity();
                        if (coinbaseMaturity <= requiredCoinbaseMaturity) {
                            if (_shouldLogInvalidTransactions) {
                                Logger.debug("Invalid Transaction. Attempted to spend coinbase before maturity. " + transactionHash);
                            }
                            return false;
                        }
                    }
                }

                final TransactionOutput transactionOutputBeingSpent = _getUnspentTransactionOutput(transactionOutputIdentifierBeingSpent);
                if (transactionOutputBeingSpent == null) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Transaction output does not exist: " + transactionOutputIdentifierBeingSpent);
                        _logInvalidTransaction(blockHeight, transaction, transactionContext);
                    }
                    return false;
                }

                totalInputValue += transactionOutputBeingSpent.getAmount();

                final LockingScript lockingScript = transactionOutputBeingSpent.getLockingScript();
                final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

                transactionContext.setTransactionInput(transactionInput);
                transactionContext.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
                transactionContext.setTransactionInputIndex(i);

                final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, transactionContext);
                if (! inputIsUnlocked) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Transaction failed to verify: " + transactionHash);
                        _logInvalidTransaction(blockHeight, transaction, transactionContext);
                    }
                    return false;
                }
            }

            totalTransactionInputValue = totalInputValue;
        }

        { // Validate that the total input value is greater than or equal to the output value...
            final long totalTransactionOutputValue;
            {
                long totalOutputValue = 0L;
                final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                if (transactionOutputs.isEmpty()) {
                    Logger.debug("Transaction contains no outputs: " + transaction.getHash());
                    return false;
                }

                for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                    final Long transactionOutputAmount = transactionOutput.getAmount();
                    if (transactionOutputAmount < 0L) {
                        Logger.debug("TransactionOutput has negative amount: " + transaction.getHash());
                        return false;
                    }
                    totalOutputValue += transactionOutputAmount;
                }
                totalTransactionOutputValue = totalOutputValue;
            }

            if (totalTransactionInputValue < totalTransactionOutputValue) {
                Logger.debug("Total TransactionInput value is less than the TransactionOutput value. (" + totalTransactionInputValue + " < " + totalTransactionOutputValue + ") Tx: " + transactionHash);
                return false;
            }
        }

        return true;
    }
}

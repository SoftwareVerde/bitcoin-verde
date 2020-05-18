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
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.HexUtil;

public class TransactionValidatorCore implements TransactionValidator {
    protected static final Object LOG_INVALID_TRANSACTION_MUTEX = new Object();
    protected static final Long COINBASE_MATURITY = TransactionValidator.COINBASE_MATURITY;

    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;
    protected final UnspentTransactionOutputSet _unspentTransactionOutputSet;
    protected final MedianBlockTimeSet _medianBlockTimeSet;
    protected final BlockOutputs _blockOutputs;

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
            Logger.debug("\n------------");
            Logger.debug("Tx Hash:\t\t\t" + transaction.getHash() + ((transactionInputIndex != null) ? ("_" + transactionInputIndex) : ("")));
            Logger.debug("Tx Bytes:\t\t\t" + HexUtil.toHexString(transactionDeflater.toBytes(transaction).getBytes()));
            Logger.debug("Tx Input:\t\t\t" + (transactionInput != null ? transactionInputDeflater.toBytes(transactionInput) : null));
            Logger.debug("Tx Output:\t\t\t" + ((outputToSpend != null) ? (outputToSpend.getIndex() + " " + transactionOutputDeflater.toBytes(outputToSpend)) : (null)));
            Logger.debug("Block Height:\t\t" + context.getBlockHeight());
            Logger.debug("Tx Input Index\t\t" + transactionInputIndex);
            Logger.debug("Locking Script:\t\t" + lockingScript);
            Logger.debug("Unlocking Script:\t\t" + unlockingScript);
            Logger.debug("Median Block Time:\t\t" + _medianBlockTime.getCurrentTimeInSeconds());
            Logger.debug("Network Time:\t\t" + _networkTime.getCurrentTimeInSeconds());
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

    protected TransactionOutput _getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        {
            final TransactionOutput transactionOutput = _unspentTransactionOutputSet.getTransactionOutput(transactionOutputIdentifier);
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
            final Long transactionOutputBlockHeight = _unspentTransactionOutputSet.getBlockHeight(transactionOutputIdentifier);
            if (transactionOutputBlockHeight != null) { return transactionOutputBlockHeight; }
        }

        if (_blockOutputs != null) {
            final TransactionOutput transactionOutput = _blockOutputs.getTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) { return blockHeight; }
        }

        return null;
    }

    protected MedianBlockTime _getTransactionOutputMedianBlockTime(final TransactionOutputIdentifier transactionOutputIdentifier, final MedianBlockTime medianBlockTime) {
        {
            final Sha256Hash transactionBlockHash = _unspentTransactionOutputSet.getBlockHash(transactionOutputIdentifier);
            final MedianBlockTime transactionOutputMedianBlockTime = _medianBlockTimeSet.getMedianBlockTime(transactionBlockHash);
            if (transactionOutputMedianBlockTime != null) { return transactionOutputMedianBlockTime; }
        }

        if (_blockOutputs != null) {
            final TransactionOutput transactionOutput = _blockOutputs.getTransactionOutput(transactionOutputIdentifier);
            if (transactionOutput != null) { return medianBlockTime; }
        }

        return null;
    }

    protected Boolean _isTransactionOutputCoinbase(final TransactionOutputIdentifier transactionOutputIdentifier) {
        {
            final Boolean transactionOutputIsCoinbase = _unspentTransactionOutputSet.isCoinbaseTransactionOutput(transactionOutputIdentifier);
            if (transactionOutputIsCoinbase != null) { return transactionOutputIsCoinbase; }
        }

        if (_blockOutputs != null) {
            final Boolean isCoinbaseTransactionOutput = _blockOutputs.isCoinbaseTransactionOutput(transactionOutputIdentifier);
            if (isCoinbaseTransactionOutput != null) { return isCoinbaseTransactionOutput; }
        }

        return null;
    }

    protected Boolean _validateSequenceNumbers(final Transaction transaction, final Long blockHeight, final Boolean validateForMemoryPool) {
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
            if (sequenceNumber.isDisabled()) { continue; }

            final TransactionOutputIdentifier previousTransactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);

            if (sequenceNumber.getType() == SequenceNumberType.SECONDS_ELAPSED) {
                final Long requiredSecondsElapsed = sequenceNumber.asSecondsElapsed();

                final MedianBlockTime medianBlockTimeOfOutputBeingSpent = _getTransactionOutputMedianBlockTime(previousTransactionOutputIdentifier, _medianBlockTime);
                final long secondsElapsed = (_medianBlockTime.getCurrentTimeInSeconds() - medianBlockTimeOfOutputBeingSpent.getCurrentTimeInSeconds());

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
                final long blockCount = ( (blockHeight - blockHeightContainingOutputBeingSpent) + (validateForMemoryPool ? 1 : 0) );
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

    protected void _logTransactionOutputNotFound(final Sha256Hash transactionHash, final TransactionInput transactionInput, final String extraMessage) {
        Logger.debug("Transaction " + transactionHash + " references non-existent output: " + transactionInput.getPreviousOutputTransactionHash() + ":" + transactionInput.getPreviousOutputIndex() + " (" + extraMessage + ")");
    }

    public TransactionValidatorCore(final UnspentTransactionOutputSet unspentTransactionOutputSet, final MedianBlockTimeSet medianBlockTimeSet, final BlockOutputs blockOutputs, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
        _unspentTransactionOutputSet = unspentTransactionOutputSet;
        _blockOutputs = blockOutputs;
        _medianBlockTimeSet = medianBlockTimeSet;
    }

    @Override
    public void setLoggingEnabled(final Boolean shouldLogInvalidTransactions) {
        _shouldLogInvalidTransactions = shouldLogInvalidTransactions;
    }

    @Override
    public Boolean validateTransaction(final Long blockHeight, final Transaction transaction, final Boolean validateForMemoryPool) {
        final Sha256Hash transactionHash = transaction.getHash();

        final ScriptRunner scriptRunner = new ScriptRunner();

        final MutableContext context = new MutableContext();
        context.setBlockHeight(blockHeight);
        context.setMedianBlockTime(_medianBlockTime);

        context.setTransaction(transaction);

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
                final Boolean lockTimeIsValid = _validateTransactionLockTime(context);
                if (! lockTimeIsValid) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Invalid LockTime for Tx.");
                    }
                    _logInvalidTransaction(transaction, context);
                    return false;
                }
            }
        }

        if (Bip68.isEnabled(blockHeight)) { // Validate Relative SequenceNumber
            if (transaction.getVersion() >= 2L) {
                final Boolean sequenceNumbersAreValid = _validateSequenceNumbers(transaction, blockHeight, validateForMemoryPool);
                if (! sequenceNumbersAreValid) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Transaction SequenceNumber validation failed.");
                    }
                    _logInvalidTransaction(transaction, context);
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

            for (int i = 0; i < transactionInputs.getCount(); ++i) {
                final TransactionInput transactionInput = transactionInputs.get(i);

                { // Enforcing Coinbase Maturity... (If the input is a coinbase then the coinbase must be at least 100 blocks old.)
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final Boolean transactionOutputBeingSpentIsCoinbaseTransaction = _isTransactionOutputCoinbase(transactionOutputIdentifier);
                    if (transactionOutputBeingSpentIsCoinbaseTransaction) {
                        final Long blockHeightOfTransactionOutputBeingSpent = _getTransactionOutputBlockHeight(transactionOutputIdentifier, blockHeight);
                        final long coinbaseMaturity = (blockHeight - blockHeightOfTransactionOutputBeingSpent);
                        if (coinbaseMaturity <= COINBASE_MATURITY) {
                            if (_shouldLogInvalidTransactions) {
                                Logger.debug("Invalid Transaction. Attempted to spend coinbase before maturity. " + transactionHash);
                            }
                            return false;
                        }
                    }
                }

                final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = TransactionOutputIdentifier.fromTransactionInput(transactionInput);

                final TransactionOutput transactionOutputBeingSpent = _getUnspentTransactionOutput(transactionOutputIdentifierBeingSpent);
                if (transactionOutputBeingSpent == null) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Transaction output does not exist: " + transactionOutputIdentifierBeingSpent);
                        _logInvalidTransaction(transaction, context);
                    }
                    return false;
                }

                totalInputValue += transactionOutputBeingSpent.getAmount();

                final LockingScript lockingScript = transactionOutputBeingSpent.getLockingScript();
                final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

                context.setTransactionInput(transactionInput);
                context.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
                context.setTransactionInputIndex(i);

                final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);
                if (! inputIsUnlocked) {
                    if (_shouldLogInvalidTransactions) {
                        Logger.debug("Transaction failed to verify: " + transactionHash);
                        _logInvalidTransaction(transaction, context);
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

                    // TODO: Validate that the output indices are sequential and start at 0... (Must check reference client if it does the same.)
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

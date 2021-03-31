package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.validator.ValidationResult;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
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
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Util;

import java.util.HashSet;

public class TransactionValidatorCore implements TransactionValidator {
    protected final Context _context;
    protected final BlockOutputs _blockOutputs;

    protected Long _getCoinbaseMaturity() {
        return TransactionValidator.COINBASE_MATURITY;
    }
    protected Integer _getMaximumSignatureOperations() {
        return TransactionValidator.MAX_SIGNATURE_OPERATIONS;
    }

    protected Json _createInvalidTransactionReport(final String errorMessage, final Transaction transaction, final TransactionContext transactionContext) {
        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();

        final TransactionOutput outputToSpend = transactionContext.getTransactionOutput();
        final TransactionInput transactionInput = transactionContext.getTransactionInput();

        final LockingScript lockingScript = (outputToSpend != null ? outputToSpend.getLockingScript() : null);
        final UnlockingScript unlockingScript = (transactionInput != null ? transactionInput.getUnlockingScript() : null);

        final Integer transactionInputIndex = transactionContext.getTransactionInputIndex();

        final MedianBlockTime medianBlockTime = transactionContext.getMedianBlockTime();
        final NetworkTime networkTime = _context.getNetworkTime();

        final Json json = new Json(false);
        json.put("errorMessage", errorMessage);
        json.put("transactionHash", transaction.getHash());
        json.put("inputIndex", transactionInputIndex);
        json.put("transactionBytes", transactionDeflater.toBytes(transaction));
        json.put("inputBytes", (transactionInput != null ? transactionInputDeflater.toBytes(transactionInput) : null));
        // json.put("transactionOutputIndex", );
        json.put("previousOutputBytes", ((outputToSpend != null) ? transactionOutputDeflater.toBytes(outputToSpend) : null));
        json.put("blockHeight", transactionContext.getBlockHeight());
        json.put("lockingScriptBytes", (lockingScript != null ? lockingScript.getBytes() : null));
        json.put("unlockingScriptBytes", (unlockingScript != null ? unlockingScript.getBytes() : null));
        json.put("medianBlockTime", (medianBlockTime != null ? medianBlockTime.getCurrentTimeInSeconds() : null));
        json.put("networkTime", (networkTime != null ? networkTime.getCurrentTimeInSeconds() : null));
        return json;
    }

    protected Boolean _shouldValidateLockTime(final Transaction transaction) {
        // If all TransactionInputs' SequenceNumbers are all final (0xFFFFFFFF) then lockTime is disregarded...

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
            if (! Util.areEqual(sequenceNumber, FINAL_SEQUENCE_NUMBER)) {
                return true;
            }
        }

        return false;
    }

    protected Boolean _validateTransactionLockTime(final TransactionContext transactionContext) {
        final UpgradeSchedule upgradeSchedule = transactionContext.getUpgradeSchedule();
        final Transaction transaction = transactionContext.getTransaction();
        final Long blockHeight = transactionContext.getBlockHeight();

        final LockTime transactionLockTime = transaction.getLockTime();
        if (transactionLockTime.getType() == LockTimeType.BLOCK_HEIGHT) {
            if (blockHeight < transactionLockTime.getValue()) { return false; }
        }
        else {
            final Long currentNetworkTime;
            {
                if (upgradeSchedule.shouldUseMedianBlockTimeForTransactionLockTime(blockHeight)) {
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

    /**
     * Returns the blockHeight of `transactionOutputIdentifier`, or `blockHeight` if the identifier is within the currently-validating block.
     *  Therefore, for chained mempool Transactions, this function returns null.
     */
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

    protected ValidationResult _validateSequenceNumbers(final Transaction transaction, final Long blockHeight) {
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
            if (sequenceNumber.isRelativeLockTimeDisabled()) { continue; }

            final TransactionOutputIdentifier previousTransactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);

            if (sequenceNumber.getType() == SequenceNumberType.SECONDS_ELAPSED) {
                final Long requiredSecondsElapsed = sequenceNumber.asSecondsElapsed();

                final Long previousBlockHeight = (blockHeight - 1L);
                final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(previousBlockHeight);
                final long secondsElapsed;
                {
                    final MedianBlockTime medianBlockTimeOfOutputBeingSpent = _getTransactionOutputMedianBlockTime(previousTransactionOutputIdentifier, blockHeight);
                    if (medianBlockTimeOfOutputBeingSpent != null) {
                        secondsElapsed = (medianBlockTime.getCurrentTimeInSeconds() - medianBlockTimeOfOutputBeingSpent.getCurrentTimeInSeconds());
                    }
                    else {
                        secondsElapsed = 0L; // No time has elapsed for outputs spending unconfirmed transactions...
                    }
                }

                final boolean sequenceNumberIsValid = (secondsElapsed >= requiredSecondsElapsed);
                if (! sequenceNumberIsValid) {
                    return ValidationResult.invalid("Sequence Number (Elapsed) Invalid: " + secondsElapsed + " < " + requiredSecondsElapsed);
                }
            }
            else {
                final Long blockHeightContainingOutputBeingSpent = _getTransactionOutputBlockHeight(previousTransactionOutputIdentifier, blockHeight);
                final long blockCount = (blockHeight - Util.coalesce(blockHeightContainingOutputBeingSpent, blockHeight)); // Uses the current blockHeight if the previousTransactionOutput is also an unconfirmed Transaction.
                final Long requiredBlockCount = sequenceNumber.asBlockCount();

                final boolean sequenceNumberIsValid = (blockCount >= requiredBlockCount);
                if (! sequenceNumberIsValid) {
                    return ValidationResult.invalid("(BlockHeight) Sequence Number Invalid: " + blockCount + " >= " + requiredBlockCount);
                }
            }
        }

        return ValidationResult.valid();
    }

    public TransactionValidatorCore(final Context context) {
        this(null, context);
    }

    public TransactionValidatorCore(final BlockOutputs blockOutputs, final Context context) {
        _context = context;
        _blockOutputs = blockOutputs;
    }

    @Override
    public TransactionValidationResult validateTransaction(final Long blockHeight, final Transaction transaction) {
        final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
        final Sha256Hash transactionHash = transaction.getHash();

        final ScriptRunner scriptRunner = new ScriptRunner(upgradeSchedule);

        final Long previousBlockHeight = (blockHeight - 1L);
        final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(previousBlockHeight);

        final MutableTransactionContext transactionContext = new MutableTransactionContext(upgradeSchedule);
        transactionContext.setBlockHeight(blockHeight);
        transactionContext.setMedianBlockTime(medianBlockTime);

        transactionContext.setTransaction(transaction);

        { // Enforce Transaction minimum byte count...
            if (upgradeSchedule.areTransactionsLessThanOneHundredBytesDisallowed(blockHeight)) {
                final Integer transactionByteCount = transaction.getByteCount();
                if (transactionByteCount < TransactionInflater.MIN_BYTE_COUNT) {
                    final Json errorJson = _createInvalidTransactionReport("Invalid byte count." + transactionByteCount + " " + transactionHash, transaction, transactionContext);
                    return TransactionValidationResult.invalid(errorJson);
                }
            }
        }

        { // Validate nLockTime...
            final Boolean shouldValidateLockTime = _shouldValidateLockTime(transaction);
            if (shouldValidateLockTime) {
                final Boolean lockTimeIsValid = _validateTransactionLockTime(transactionContext);
                if (! lockTimeIsValid) {
                    final Json errorJson = _createInvalidTransactionReport("Invalid LockTime.", transaction, transactionContext);
                    return TransactionValidationResult.invalid(errorJson);
                }
            }
        }

        if (upgradeSchedule.isRelativeLockTimeEnabled(blockHeight)) { // Validate Relative SequenceNumber
            if (transaction.getVersion() >= 2L) {
                final ValidationResult sequenceNumbersValidationResult = _validateSequenceNumbers(transaction, blockHeight);
                if (! sequenceNumbersValidationResult.isValid) {
                    final Json errorJson = _createInvalidTransactionReport(sequenceNumbersValidationResult.errorMessage, transaction, transactionContext);
                    return TransactionValidationResult.invalid(errorJson);
                }
            }
        }

        final long totalTransactionInputValue;
        {
            long totalInputValueCounter = 0L;

            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

            if (transactionInputs.isEmpty()) {
                final Json errorJson = _createInvalidTransactionReport("Transaction contained missing TransactionInputs.", transaction, transactionContext);
                return TransactionValidationResult.invalid(errorJson);
            }

            final int transactionInputCount = transactionInputs.getCount();
            final HashSet<TransactionOutputIdentifier> spentOutputIdentifiers = new HashSet<TransactionOutputIdentifier>(transactionInputCount);

            for (int i = 0; i < transactionInputCount; ++i) {
                final TransactionInput transactionInput = transactionInputs.get(i);
                final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                final boolean previousOutputIsUniqueToTransaction = spentOutputIdentifiers.add(transactionOutputIdentifierBeingSpent);
                if (! previousOutputIsUniqueToTransaction) { // The transaction attempted to spend the same previous output twice...
                    final Json errorJson = _createInvalidTransactionReport("Transaction spends the same output twice.", transaction, transactionContext);
                    return TransactionValidationResult.invalid(errorJson);
                }

                { // Enforce Coinbase Maturity... (If the input is a coinbase then the coinbase must be at least 100 blocks old.)
                    final Boolean transactionOutputBeingSpentIsCoinbaseTransaction = _isTransactionOutputCoinbase(transactionOutputIdentifierBeingSpent);
                    if (transactionOutputBeingSpentIsCoinbaseTransaction == null) {
                        final Json errorJson = _createInvalidTransactionReport("Previous output does not exist.", transaction, transactionContext);
                        return TransactionValidationResult.invalid(errorJson);
                    }

                    if (transactionOutputBeingSpentIsCoinbaseTransaction) {
                        final Long blockHeightOfTransactionOutputBeingSpent = _getTransactionOutputBlockHeight(transactionOutputIdentifierBeingSpent, blockHeight);
                        final long coinbaseMaturity = (blockHeight - blockHeightOfTransactionOutputBeingSpent);
                        final Long requiredCoinbaseMaturity = _getCoinbaseMaturity();
                        if (coinbaseMaturity <= requiredCoinbaseMaturity) {
                            final Json errorJson = _createInvalidTransactionReport("Attempted to spend coinbase before maturity.", transaction, transactionContext);
                            return TransactionValidationResult.invalid(errorJson);
                        }
                    }
                }

                final TransactionOutput transactionOutputBeingSpent = _getUnspentTransactionOutput(transactionOutputIdentifierBeingSpent);
                if (transactionOutputBeingSpent == null) {
                    final Json errorJson = _createInvalidTransactionReport("Transaction output does not exist or has been spent.", transaction, transactionContext);
                    return TransactionValidationResult.invalid(errorJson);
                }

                totalInputValueCounter += transactionOutputBeingSpent.getAmount();

                final LockingScript lockingScript = transactionOutputBeingSpent.getLockingScript();
                final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

                transactionContext.setTransactionInput(transactionInput);
                transactionContext.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
                transactionContext.setTransactionInputIndex(i);

                if (unlockingScript.getByteCount() > MAX_SCRIPT_BYTE_COUNT) {
                    final Json errorJson = _createInvalidTransactionReport("Transaction unlocking script exceeds max size.", transaction, transactionContext);
                    return TransactionValidationResult.invalid(errorJson);
                }

                final ScriptRunner.ScriptRunnerResult scriptRunnerResult = scriptRunner.runScript(lockingScript, unlockingScript, transactionContext);
                final boolean inputIsUnlocked = scriptRunnerResult.isValid;
                if (! inputIsUnlocked) {
                    final Json errorJson = _createInvalidTransactionReport("Transaction failed to unlock inputs.", transaction, transactionContext);
                    return TransactionValidationResult.invalid(errorJson);
                }

                final Integer signatureOperationCount = scriptRunnerResult.signatureOperationCount;
                transactionContext.incrementSignatureOperationCount(signatureOperationCount);
            }

            totalTransactionInputValue = totalInputValueCounter;

        }

        { // Validate that the total input value is greater than or equal to the output value...
            final long totalTransactionOutputValue;
            {
                long totalOutputValue = 0L;
                final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                if (transactionOutputs.isEmpty()) {
                    final Json errorJson = _createInvalidTransactionReport("Transaction contains no outputs.", transaction, transactionContext);
                    return TransactionValidationResult.invalid(errorJson);
                }

                for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                    final LockingScript lockingScript = transactionOutput.getLockingScript();
                    if (lockingScript.getByteCount() > MAX_SCRIPT_BYTE_COUNT) {
                        final Json errorJson = _createInvalidTransactionReport("Transaction locking script exceeds max size.", transaction, transactionContext);
                        return TransactionValidationResult.invalid(errorJson);
                    }

                    final Long transactionOutputAmount = transactionOutput.getAmount();
                    if (transactionOutputAmount < 0L) {
                        final Json errorJson = _createInvalidTransactionReport("TransactionOutput has negative amount.", transaction, transactionContext);
                        return TransactionValidationResult.invalid(errorJson);
                    }
                    totalOutputValue += transactionOutputAmount;
                }
                totalTransactionOutputValue = totalOutputValue;
            }

            if (totalTransactionInputValue < totalTransactionOutputValue) {
                final Json errorJson = _createInvalidTransactionReport("Total TransactionInput value is less than the TransactionOutput value.", transaction, transactionContext);
                return TransactionValidationResult.invalid(errorJson);
            }
        }

        final Integer transactionSignatureOperationCount = transactionContext.getSignatureOperationCount();
        if (upgradeSchedule.isSignatureOperationCountingVersionTwoEnabled(medianBlockTime)) { // Enforce maximum Signature operations per Transaction...
            final Integer maximumSignatureOperationCount = _getMaximumSignatureOperations();
            if (transactionSignatureOperationCount > maximumSignatureOperationCount) {
                final Json errorJson = _createInvalidTransactionReport("Transaction exceeds maximum signature operation count.", transaction, transactionContext);
                return TransactionValidationResult.invalid(errorJson);
            }
        }

        return TransactionValidationResult.valid(transactionSignatureOperationCount);
    }
}

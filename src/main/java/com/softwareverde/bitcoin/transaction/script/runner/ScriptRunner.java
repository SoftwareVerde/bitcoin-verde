package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.ArithmeticOperation;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.runner.context.TransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.Logger;

/**
 * NOTE: It seems that all values within Bitcoin Core scripts are stored as little-endian.
 *  To remain consistent with the rest of this library, all values are converted from little-endian
 *  to big-endian for all internal (in-memory) purposes, and then reverted to little-endian when stored.
 *
 * NOTE: All Operation Math and Values appear to be injected into the script as 4-byte integers.
 */
public class ScriptRunner {
    public static class ScriptRunnerResult {
        public static ScriptRunnerResult valid(final TransactionContext transactionContext) {
            final Integer signatureOperationCount = transactionContext.getSignatureOperationCount();
            return new ScriptRunnerResult(true, signatureOperationCount);
        }

        public static ScriptRunnerResult invalid(final TransactionContext transactionContext) {
            final Integer signatureOperationCount = transactionContext.getSignatureOperationCount();
            return new ScriptRunnerResult(false, signatureOperationCount);
        }

        public final Boolean isValid;
        public final Integer signatureOperationCount;

        public ScriptRunnerResult(final Boolean isValid, final Integer signatureOperationCount) {
            this.isValid = isValid;
            this.signatureOperationCount = signatureOperationCount;
        }
    }

    protected Boolean _failIfPresent(final Operation operation, final TransactionContext context) {
        if (operation.failIfPresent()) { return true; }

        // May be removed after 20220515HF...
        final MedianBlockTime medianBlockTime = context.getMedianBlockTime();
        if (! _upgradeSchedule.isMultiplyOperationEnabled(medianBlockTime)) {
            if (operation instanceof ArithmeticOperation) {
                final ArithmeticOperation arithmeticOperation = (ArithmeticOperation) operation;
                final Opcode opcode = arithmeticOperation.getOpcode();
                if (opcode == Opcode.MULTIPLY) { return true; }
            }
        }

        return false;
    }

    protected Boolean _shouldRunPayToScriptHashScript(final LockingScript lockingScript, final UnlockingScript unlockingScript, final MedianBlockTime medianBlockTime, final Long blockHeight) {
        final boolean scriptIsPayToScriptHash = (lockingScript.getScriptType() == ScriptType.PAY_TO_SCRIPT_HASH);
        if (! scriptIsPayToScriptHash) { return false; }

        final Boolean legacyPayToScriptHashIsEnabled = _upgradeSchedule.isLegacyPayToScriptHashEnabled(blockHeight);
        final Boolean sha256PayToScriptHashIsEnabled = _upgradeSchedule.isSha256PayToScriptHashEnabled(medianBlockTime);
        if ((! legacyPayToScriptHashIsEnabled) && (! sha256PayToScriptHashIsEnabled)) { return false; }

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final boolean isLegacyPayToScriptHashFormat = scriptPatternMatcher.matchesLegacyPayToScriptHashFormat(lockingScript);
        if ( isLegacyPayToScriptHashFormat && (! legacyPayToScriptHashIsEnabled) ) { return false; }

        final boolean isSha256PayToScriptHashFormat = (! isLegacyPayToScriptHashFormat);
        if ( isSha256PayToScriptHashFormat && (! sha256PayToScriptHashIsEnabled) ) { return false; }

        final Boolean segwitRecoveryIsEnabled = _upgradeSchedule.areUnusedValuesAfterSegwitScriptExecutionAllowed(medianBlockTime);
        if (segwitRecoveryIsEnabled) {
            final Boolean isLegacyPayToScriptHashLockingScript = scriptPatternMatcher.matchesLegacyPayToScriptHashFormat(lockingScript); // https://gitlab.com/0353F40E/p2sh32/-/blob/main/CHIP-2022-05_Pay-to-Script-Hash-32_(P2SH32)_for_Bitcoin_Cash.md#segwit-recovery

            // NOTE: Contrary to the original specification, Bitcoin ABC's 0.19 behavior does not run P2SH Scripts that match the Segwit format...
            final boolean unlockingScriptIsSegregatedWitnessProgram = (isLegacyPayToScriptHashLockingScript && scriptPatternMatcher.matchesSegregatedWitnessProgram(unlockingScript));
            if (unlockingScriptIsSegregatedWitnessProgram) { return false; }
        }

        return true;
    }

    protected final UpgradeSchedule _upgradeSchedule;

    public ScriptRunner(final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
    }

    public ScriptRunnerResult runScript(final LockingScript lockingScript, final UnlockingScript unlockingScript, final TransactionContext transactionContext) {
        final Long blockHeight = transactionContext.getBlockHeight();
        final MedianBlockTime medianBlockTime = transactionContext.getMedianBlockTime();

        final MutableTransactionContext mutableContext = new MutableTransactionContext(transactionContext);
        mutableContext.clearSignatureOperationCount();

        final ControlState controlState = new ControlState();

        final int lockingScriptByteCount = lockingScript.getByteCount();
        if (lockingScriptByteCount > Script.MAX_SPENDABLE_SCRIPT_BYTE_COUNT) {
            return ScriptRunnerResult.invalid(mutableContext);
        }
        final int unlockingScriptByteCount = unlockingScript.getByteCount();
        if (unlockingScriptByteCount > Script.MAX_SPENDABLE_SCRIPT_BYTE_COUNT) {
            return ScriptRunnerResult.invalid(mutableContext);
        }

        final Stack traditionalStack;
        final Stack payToScriptHashStack;

        { // Normal Script-Validation...
            traditionalStack = new Stack();
            traditionalStack.setMaxItemCount(1000);
            try {
                final List<Operation> unlockingScriptOperations = unlockingScript.getOperations();
                if (unlockingScriptOperations == null) {
                    return ScriptRunnerResult.invalid(mutableContext);
                }

                if (_upgradeSchedule.areOnlyPushOperationsAllowedWithinUnlockingScript(blockHeight)) {
                    final Boolean unlockingScriptContainsNonPushOperations = unlockingScript.containsNonPushOperations();
                    if (unlockingScriptContainsNonPushOperations) { // Only push operations are allowed in the unlocking script. (BIP 62)
                        return ScriptRunnerResult.invalid(mutableContext);
                    }
                }

                mutableContext.setCurrentScript(unlockingScript);
                for (final Operation operation : unlockingScriptOperations) {
                    mutableContext.incrementCurrentScriptIndex();

                    if (! _incrementAndCheckOperationCount(operation, mutableContext)) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }

                    if (_failIfPresent(operation, mutableContext)) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }

                    final Boolean shouldExecute = operation.shouldExecute(traditionalStack, controlState, mutableContext);
                    if (! shouldExecute) { continue; }

                    final Boolean wasSuccessful = operation.applyTo(traditionalStack, controlState, mutableContext);
                    if (! wasSuccessful) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }
                }

                if (controlState.isInCodeBlock()) { // IF/ELSE blocks cannot span scripts.
                    return ScriptRunnerResult.invalid(mutableContext);
                }

                traditionalStack.clearAltStack(); // Clear the alt stack for the unlocking script, and for the payToScriptHash script...

                payToScriptHashStack = new Stack(traditionalStack);

                final List<Operation> lockingScriptOperations = lockingScript.getOperations();
                if (lockingScriptOperations == null) {
                    return ScriptRunnerResult.invalid(mutableContext);
                }

                mutableContext.setCurrentScript(lockingScript);
                for (final Operation operation : lockingScriptOperations) {
                    mutableContext.incrementCurrentScriptIndex();

                    if (! _incrementAndCheckOperationCount(operation, mutableContext)) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }

                    if (_failIfPresent(operation, mutableContext)) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }

                    final Boolean shouldExecute = operation.shouldExecute(traditionalStack, controlState, mutableContext);
                    if (! shouldExecute) { continue; }

                    final Boolean wasSuccessful = operation.applyTo(traditionalStack, controlState, mutableContext);
                    if (! wasSuccessful) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }
                }
            }
            catch (final Exception exception) {
                Logger.warn(exception);
                return ScriptRunnerResult.invalid(mutableContext);
            }

            { // Validate Stack...
                if (traditionalStack.didOverflow()) {
                    return ScriptRunnerResult.invalid(mutableContext);
                }
                if (traditionalStack.isEmpty()) {
                    return ScriptRunnerResult.invalid(mutableContext);
                }
                final Value topStackValue = traditionalStack.pop();
                if (! topStackValue.asBoolean()) {
                    return ScriptRunnerResult.invalid(mutableContext);
                }
            }
        }

        final boolean shouldRunPayToScriptHashScript;
        { // Pay-To-Script-Hash Validation
            shouldRunPayToScriptHashScript = _shouldRunPayToScriptHashScript(lockingScript, unlockingScript, medianBlockTime, blockHeight);

            if (shouldRunPayToScriptHashScript) {
                final Boolean unlockingScriptContainsNonPushOperations = unlockingScript.containsNonPushOperations();
                if (unlockingScriptContainsNonPushOperations) {
                    return ScriptRunnerResult.invalid(mutableContext);
                }

                try {
                    final Value redeemScriptValue = payToScriptHashStack.pop();
                    if (payToScriptHashStack.didOverflow()) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }
                    final Script redeemScript = new ImmutableScript(redeemScriptValue);

                    mutableContext.setCurrentScript(redeemScript);
                    final List<Operation> redeemScriptOperations = redeemScript.getOperations();
                    if (redeemScriptOperations == null) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }

                    for (final Operation operation : redeemScriptOperations) {
                        mutableContext.incrementCurrentScriptIndex();

                        if (! _incrementAndCheckOperationCount(operation, mutableContext)) {
                            return ScriptRunnerResult.invalid(mutableContext);
                        }

                        if (_failIfPresent(operation, mutableContext)) {
                            return ScriptRunnerResult.invalid(mutableContext);
                        }

                        final Boolean shouldExecute = operation.shouldExecute(payToScriptHashStack, controlState, mutableContext);
                        if (! shouldExecute) { continue; }

                        final Boolean wasSuccessful = operation.applyTo(payToScriptHashStack, controlState, mutableContext);
                        if (! wasSuccessful) {
                            return ScriptRunnerResult.invalid(mutableContext);
                        }
                    }
                }
                catch (final Exception exception) {
                    Logger.warn(exception);
                    return ScriptRunnerResult.invalid(mutableContext);
                }

                { // Validate P2SH Stack...
                    if (payToScriptHashStack.isEmpty()) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }
                    final Value topStackValue = payToScriptHashStack.pop();
                    if (! topStackValue.asBoolean()) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }
                }
            }
        }

        if (controlState.isInCodeBlock()) { // All CodeBlocks must be closed before the end of the script...
            return ScriptRunnerResult.invalid(mutableContext);
        }

        // Dirty stacks are considered invalid after HF20181115 in order to reduce malleability...
        if (_upgradeSchedule.areUnusedValuesAfterScriptExecutionDisallowed(blockHeight)) {
            final Stack stack = (shouldRunPayToScriptHashScript ? payToScriptHashStack : traditionalStack);
            if (! stack.isEmpty()) {
                if (_upgradeSchedule.areUnusedValuesAfterSegwitScriptExecutionAllowed(medianBlockTime)) {
                    final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
                    final Boolean unlockingScriptIsSegregatedWitnessProgram = scriptPatternMatcher.matchesSegregatedWitnessProgram(unlockingScript);
                    if (! (shouldRunPayToScriptHashScript && unlockingScriptIsSegregatedWitnessProgram)) {
                        return ScriptRunnerResult.invalid(mutableContext);
                    }
                }
                else {
                    return ScriptRunnerResult.invalid(mutableContext);
                }
            }
        }

        return ScriptRunnerResult.valid(mutableContext);
    }

    private Boolean _incrementAndCheckOperationCount(final Operation operation, final MutableTransactionContext mutableContext) {
        if (ByteUtil.byteToInteger(operation.getOpcodeByte()) > Opcode.PUSH_VALUE.getMaxValue()) {
            mutableContext.incrementOperationCount(1);

            if (! _upgradeSchedule.areBigScriptIntegersEnabled(mutableContext.getMedianBlockTime())) {
                if (mutableContext.getOperationCount() > Script.MAX_OPERATION_COUNT) {
                    Logger.debug("Maximum number of operations exceeded.");
                    return false;
                }
            }
        }
        return true;
    }
}

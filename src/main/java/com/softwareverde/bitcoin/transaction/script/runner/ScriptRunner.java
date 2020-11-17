package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.runner.context.TransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
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
    public static final Integer MAX_SCRIPT_BYTE_COUNT = 10000;

    protected static final Boolean BITCOIN_ABC_QUIRK_ENABLED = true;

    protected final UpgradeSchedule _upgradeSchedule;

    public ScriptRunner(final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
    }

    public Boolean runScript(final LockingScript lockingScript, final UnlockingScript unlockingScript, final TransactionContext transactionContext) {
        final Long blockHeight = transactionContext.getBlockHeight();
        final MedianBlockTime medianBlockTime = transactionContext.getMedianBlockTime();
        final MutableTransactionContext mutableContext = new MutableTransactionContext(transactionContext);

        final ControlState controlState = new ControlState();

        if (lockingScript.getByteCount() > MAX_SCRIPT_BYTE_COUNT) { return false; }
        if (unlockingScript.getByteCount() > MAX_SCRIPT_BYTE_COUNT) { return false; }

        final Stack traditionalStack;
        final Stack payToScriptHashStack;

        { // Normal Script-Validation...
            traditionalStack = new Stack();
            traditionalStack.setMaxItemCount(1000);
            try {
                final List<Operation> unlockingScriptOperations = unlockingScript.getOperations();
                if (unlockingScriptOperations == null) { return false; }

                if (_upgradeSchedule.disallowNonPushOperationsWithinUnlockingScript(blockHeight)) {
                    final Boolean unlockingScriptContainsNonPushOperations = unlockingScript.containsNonPushOperations();
                    if (unlockingScriptContainsNonPushOperations) { return false; } // Only push operations are allowed in the unlocking script. (BIP 62)
                }

                mutableContext.setCurrentScript(unlockingScript);
                for (final Operation operation : unlockingScriptOperations) {
                    mutableContext.incrementCurrentScriptIndex();

                    if (operation.failIfPresent()) { return false; }

                    final Boolean shouldExecute = operation.shouldExecute(traditionalStack, controlState, mutableContext);
                    if (! shouldExecute) { continue; }

                    final Boolean wasSuccessful = operation.applyTo(traditionalStack, controlState, mutableContext);
                    if (! wasSuccessful) { return false; }
                }

                if (controlState.isInCodeBlock()) { return false; } // IF/ELSE blocks cannot span scripts.

                traditionalStack.clearAltStack(); // Clear the alt stack for the unlocking script, and for the payToScriptHash script...

                payToScriptHashStack = new Stack(traditionalStack);

                final List<Operation> lockingScriptOperations = lockingScript.getOperations();
                if (lockingScriptOperations == null) { return false; }

                mutableContext.setCurrentScript(lockingScript);
                for (final Operation operation : lockingScriptOperations) {
                    mutableContext.incrementCurrentScriptIndex();

                    if (operation.failIfPresent()) { return false; }

                    final Boolean shouldExecute = operation.shouldExecute(traditionalStack, controlState, mutableContext);
                    if (! shouldExecute) { continue; }

                    final Boolean wasSuccessful = operation.applyTo(traditionalStack, controlState, mutableContext);
                    if (! wasSuccessful) { return false; }
                }
            }
            catch (final Exception exception) {
                Logger.warn(exception);
                return false;
            }

            { // Validate Stack...
                if (traditionalStack.didOverflow()) { return false; }
                if (traditionalStack.isEmpty()) { return false; }
                final Value topStackValue = traditionalStack.pop();
                if (! topStackValue.asBoolean()) { return false; }
            }
        }

        final boolean shouldRunPayToScriptHashScript;
        { // Pay-To-Script-Hash Validation
            final Boolean payToScriptHashValidationRulesAreEnabled = _upgradeSchedule.isPayToScriptHashEnabled(blockHeight);
            final Boolean scriptIsPayToScriptHash = (lockingScript.getScriptType() == ScriptType.PAY_TO_SCRIPT_HASH);

            if (BITCOIN_ABC_QUIRK_ENABLED) {
                // NOTE: Bitcoin ABC's 0.19 behavior does not run P2SH Scripts that match the Segwit format...
                final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
                final Boolean unlockingScriptIsSegregatedWitnessProgram = scriptPatternMatcher.matchesSegregatedWitnessProgram(unlockingScript);
                shouldRunPayToScriptHashScript = ( payToScriptHashValidationRulesAreEnabled && scriptIsPayToScriptHash && (! unlockingScriptIsSegregatedWitnessProgram) );
            }
            else {
                shouldRunPayToScriptHashScript = ( payToScriptHashValidationRulesAreEnabled && scriptIsPayToScriptHash );
            }

            if (shouldRunPayToScriptHashScript) {
                final Boolean unlockingScriptContainsNonPushOperations = unlockingScript.containsNonPushOperations();
                if (unlockingScriptContainsNonPushOperations) { return false; }

                try {
                    final Value redeemScriptValue = payToScriptHashStack.pop();
                    if (payToScriptHashStack.didOverflow()) { return false; }
                    final Script redeemScript = new ImmutableScript(redeemScriptValue);

                    mutableContext.setCurrentScript(redeemScript);
                    final List<Operation> redeemScriptOperations = redeemScript.getOperations();
                    if (redeemScriptOperations == null) { return false; }

                    for (final Operation operation : redeemScriptOperations) {
                        mutableContext.incrementCurrentScriptIndex();

                        if (operation.failIfPresent()) { return false; }

                        final Boolean shouldExecute = operation.shouldExecute(payToScriptHashStack, controlState, mutableContext);
                        if (! shouldExecute) { continue; }
                        
                        final Boolean wasSuccessful = operation.applyTo(payToScriptHashStack, controlState, mutableContext);
                        if (! wasSuccessful) { return false; }
                    }
                }
                catch (final Exception exception) {
                    Logger.warn(exception);
                    return false;
                }

                { // Validate P2SH Stack...
                    if (payToScriptHashStack.isEmpty()) { return false; }
                    final Value topStackValue = payToScriptHashStack.pop();
                    if (! topStackValue.asBoolean()) { return false; }
                }
            }
        }

        if (controlState.isInCodeBlock()) { return false; } // All CodeBlocks must be closed before the end of the script...

        // Dirty stacks are considered invalid after HF20181115 in order to reduce malleability...
        if (_upgradeSchedule.disallowUnusedValuesAfterScriptExecution(blockHeight)) {
            final Stack stack = (shouldRunPayToScriptHashScript ? payToScriptHashStack : traditionalStack);
            if (! stack.isEmpty()) {
                if (_upgradeSchedule.allowUnusedValuesAfterScriptExecutionForSegwitScripts(medianBlockTime)) {
                    final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
                    final Boolean unlockingScriptIsSegregatedWitnessProgram = scriptPatternMatcher.matchesSegregatedWitnessProgram(unlockingScript);
                    if (! (shouldRunPayToScriptHashScript && unlockingScriptIsSegregatedWitnessProgram)) { return false; }
                }
                else { return false; }
            }
        }

        return true;
    }
}

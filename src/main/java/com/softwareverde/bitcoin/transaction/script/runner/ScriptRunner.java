package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.bip.Bip16;
import com.softwareverde.bitcoin.bip.HF20181115;
import com.softwareverde.bitcoin.bip.HF20181115SV;
import com.softwareverde.bitcoin.bip.HF20190515;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.runner.context.Context;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;

/**
 * NOTE: It seems that all values within Bitcoin Core scripts are stored as little-endian.
 *  To remain consistent with the rest of this library, all values are converted from little-endian
 *  to big-endian for all internal (in-memory) purposes, and then reverted to little-endian when stored.
 *
 * NOTE: All Operation Math and Values appear to be injected into the script as 4-byte integers.
 */
public class ScriptRunner {
    protected static final Boolean BITCOIN_ABC_QUIRK_ENABLED = true;

    protected final MedianBlockTime _medianBlockTime;

    public ScriptRunner(final MedianBlockTime medianBlockTime) {
        _medianBlockTime = medianBlockTime;
    }

    public Boolean runScript(final LockingScript lockingScript, final UnlockingScript unlockingScript, final Context context) {
        final MutableContext mutableContext = new MutableContext(context);

        final ControlState controlState = new ControlState();

        final Stack traditionalStack;
        final Stack payToScriptHashStack;

        { // Normal Script-Validation...
            traditionalStack = new Stack();
            try {
                final List<Operation> unlockingScriptOperations = unlockingScript.getOperations();
                if (unlockingScriptOperations == null) { return false; }

                if (HF20181115.isEnabled(context.getBlockHeight())) {
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
                Logger.log(exception);
                return false;
            }

            { // Validate Stack...
                if (traditionalStack.isEmpty()) { return false; }
                final Value topStackValue = traditionalStack.pop();
                if (! topStackValue.asBoolean()) { return false; }
            }
        }

        final Boolean shouldRunPayToScriptHashScript;
        { // Pay-To-Script-Hash Validation
            final Boolean payToScriptHashValidationRulesAreEnabled = Bip16.isEnabled(mutableContext.getBlockHeight());
            final Boolean scriptIsPayToScriptHash = (lockingScript.getScriptType() == ScriptType.PAY_TO_SCRIPT_HASH);

            if (BITCOIN_ABC_QUIRK_ENABLED) {
                // NOTE: Bitcoin ABC's 0.19 behavior does not run P2SH Scripts that match the Segwit format...
                final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
                final Boolean unlockingScriptIsSegregatedWitnessProgram = scriptPatternMatcher.matchesSegregatedWitnessProgram(unlockingScript);
                shouldRunPayToScriptHashScript = ( (payToScriptHashValidationRulesAreEnabled) && (scriptIsPayToScriptHash) && (! unlockingScriptIsSegregatedWitnessProgram) );
            }
            else {
                shouldRunPayToScriptHashScript = ( (payToScriptHashValidationRulesAreEnabled) && (scriptIsPayToScriptHash) );
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
                    Logger.log(exception);
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
        if ( (HF20181115.isEnabled(context.getBlockHeight())) && (! HF20181115SV.isEnabled(context.getBlockHeight())) ) {
            final Stack stack = (shouldRunPayToScriptHashScript ? payToScriptHashStack : traditionalStack);
            if (! stack.isEmpty()) {
                if (HF20190515.isEnabled(_medianBlockTime)) {
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

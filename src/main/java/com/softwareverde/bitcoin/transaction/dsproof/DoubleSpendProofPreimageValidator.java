package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.Util;

public class DoubleSpendProofPreimageValidator {
    protected final Long _blockHeight;
    protected final MedianBlockTime _medianBlockTime;
    protected final UpgradeSchedule _upgradeSchedule;

    protected UnlockingScript _modifyUnlockingScript(final ScriptType previousTransactionScriptType, final UnlockingScript originalUnlockingScript, final DoubleSpendProofPreimage doubleSpendProofPreimage) {
        final List<ByteArray> pushedBytes = doubleSpendProofPreimage.getUnlockingScriptPushData();

        final MutableUnlockingScript mutableUnlockingScript = new MutableUnlockingScript();

        // Copy over all of the DSProof pushed-data as push-opcodes...
        for (final ByteArray byteArray : pushedBytes) {
            final PushOperation pushOperation = PushOperation.pushBytes(byteArray);
            mutableUnlockingScript.addOperation(pushOperation);
        }

        if ( (previousTransactionScriptType == ScriptType.PAY_TO_SCRIPT_HASH) || (previousTransactionScriptType == ScriptType.PAY_TO_PUBLIC_KEY_HASH) ) {
            // For P2PKH and P2SH scripts, the push-data field is optimized to only include the unique portions of the
            //  script, therefore the last opcode of the originalUnlockingScript is retained (which is either the public
            //  key or the P2SH script, respectively).

            final List<Operation> originalOperations = originalUnlockingScript.getOperations();
            final int originalOperationsCount = originalOperations.getCount();

            { // Push the last operation of the original script...
                final int index = (originalOperationsCount - 1);
                if (index < 0) { return null; } // Impossible for P2PKH/P2SH.

                final Operation retainedOperation = originalOperations.get(index);
                mutableUnlockingScript.addOperation(retainedOperation);
            }
        }

        return mutableUnlockingScript;
    }

    protected Integer _getInputIndexSpendingOutput(final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent, final Transaction transaction) {
        int i = 0;
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            if (Util.areEqual(transactionOutputIdentifierBeingSpent, transactionOutputIdentifier)) {
                return i;
            }
            i += 1;
        }

        return null;
    }

    protected TransactionInput _getInputSpendingOutput(final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent, final Transaction transaction) {
        final Integer inputIndex = _getInputIndexSpendingOutput(transactionOutputIdentifierBeingSpent, transaction);
        if (inputIndex == null) { return null; }

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        return transactionInputs.get(inputIndex);
    }

    protected UnlockingScript _getUnlockingScriptSpendingOutput(final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent, final Transaction transaction) {
        final TransactionInput transactionInput = _getInputSpendingOutput(transactionOutputIdentifierBeingSpent, transaction);
        if (transactionInput == null) { return null; }

        return transactionInput.getUnlockingScript();
    }

    public DoubleSpendProofPreimageValidator(final Long blockHeight, final MedianBlockTime medianBlockTime, final UpgradeSchedule upgradeSchedule) {
        _blockHeight = blockHeight;
        _medianBlockTime = medianBlockTime;
        _upgradeSchedule = upgradeSchedule;
    }

    public Boolean validateDoubleSpendProof(final TransactionOutputIdentifier transactionOutputBeingSpentIdentifier, final TransactionOutput transactionOutputBeingSpent, final Transaction firstSeenSpendingTransaction, final DoubleSpendProofPreimage doubleSpendProofPreimage) {
        final LockingScript lockingScript = transactionOutputBeingSpent.getLockingScript();
        final UnlockingScript firstSeenUnlockingScript = _getUnlockingScriptSpendingOutput(transactionOutputBeingSpentIdentifier, firstSeenSpendingTransaction);
        if (firstSeenUnlockingScript == null) { return false; }

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);

        final UnlockingScript unlockingScript = _modifyUnlockingScript(scriptType, firstSeenUnlockingScript, doubleSpendProofPreimage);
        if (unlockingScript == null) { return false; }

        final TransactionSigner transactionSigner = new DoubleSpendProofPreimageTransactionSigner(doubleSpendProofPreimage);
        final MutableTransactionContext transactionContext = new MutableTransactionContext(_upgradeSchedule, transactionSigner);
        {
            transactionContext.setBlockHeight(_blockHeight);
            transactionContext.setMedianBlockTime(_medianBlockTime);
            transactionContext.setTransaction(firstSeenSpendingTransaction);

            final List<TransactionInput> transactionInputs = firstSeenSpendingTransaction.getTransactionInputs();
            final Integer transactionInputIndex = _getInputIndexSpendingOutput(transactionOutputBeingSpentIdentifier, firstSeenSpendingTransaction);
            if (transactionInputIndex == null) { return false; }
            final TransactionInput transactionInput = transactionInputs.get(transactionInputIndex);

            transactionContext.setTransactionInput(transactionInput);
            transactionContext.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
            transactionContext.setTransactionInputIndex(transactionInputIndex);
        }

        final ScriptRunner scriptRunner = new ScriptRunner(_upgradeSchedule);
        final ScriptRunner.ScriptRunnerResult scriptRunnerResult = scriptRunner.runScript(lockingScript, unlockingScript, transactionContext);
        return scriptRunnerResult.isValid;
    }
}

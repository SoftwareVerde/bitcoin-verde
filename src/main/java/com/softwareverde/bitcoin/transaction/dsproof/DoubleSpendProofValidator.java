package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
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

public class DoubleSpendProofValidator {
    protected final UpgradeSchedule _upgradeSchedule;

    protected UnlockingScript _modifyLockingScript(final UnlockingScript originalUnlockingScript, final DoubleSpendProofPreimage doubleSpendProofPreimage) {
        if (originalUnlockingScript == null) { return null; }

        final List<Operation> originalOperations = originalUnlockingScript.getOperations();
        final int originalOperationsCount = originalOperations.getCount();

        if (originalUnlockingScript.containsNonPushOperations()) { return null; }

        final List<ByteArray> pushedBytes = doubleSpendProofPreimage.getUnlockingScriptPushData();
        if (pushedBytes.getCount() > originalOperationsCount) { return null; }

        final MutableUnlockingScript mutableUnlockingScript = new MutableUnlockingScript();

        { // Copy over any operations that are to be retained from the original script...
            final int operationsToRetainCount = (originalOperationsCount - pushedBytes.getCount());
            for (int i = 0; i < operationsToRetainCount; ++i) {
                final Operation retainedOperation = originalOperations.get(i);
                mutableUnlockingScript.addOperation(retainedOperation);
            }
        }

        for (final ByteArray byteArray : pushedBytes) {
            final PushOperation pushOperation = PushOperation.pushBytes(byteArray);
            mutableUnlockingScript.addOperation(pushOperation);
        }

        return mutableUnlockingScript;
    }

    protected UnlockingScript _getUnlockingScriptSpendingOutput(final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent, final Transaction transaction) {
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            if (Util.areEqual(transactionOutputIdentifierBeingSpent, transactionOutputIdentifier)) {
                return transactionInput.getUnlockingScript();
            }
        }

        return null;
    }

    public DoubleSpendProofValidator(final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
    }

    public Boolean validateDoubleSpendProof(final TransactionOutputIdentifier transactionOutputBeingSpentIdentifier, final TransactionOutput transactionOutputBeingSpent, final Transaction firstSeenSpendingTransaction, final DoubleSpendProofPreimage doubleSpendProofPreimage) {
        final LockingScript lockingScript = transactionOutputBeingSpent.getLockingScript();
        final UnlockingScript firstSeenUnlockingScript = _getUnlockingScriptSpendingOutput(transactionOutputBeingSpentIdentifier, firstSeenSpendingTransaction);
        if (firstSeenUnlockingScript == null) { return false; }

        final UnlockingScript unlockingScript = _modifyLockingScript(firstSeenUnlockingScript, doubleSpendProofPreimage);
        if (unlockingScript == null) { return false; }

        final TransactionSigner transactionSigner = new DoubleSpendProofPreimageTransactionSigner(doubleSpendProofPreimage);
        final MutableTransactionContext transactionContext = new MutableTransactionContext(_upgradeSchedule, transactionSigner);

        final ScriptRunner scriptRunner = new ScriptRunner(_upgradeSchedule);
        final ScriptRunner.ScriptRunnerResult scriptRunnerResult = scriptRunner.runScript(lockingScript, unlockingScript, transactionContext);
        return scriptRunnerResult.isValid;
    }
}

package com.softwareverde.bitcoin.transaction.script.memo;

import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.constable.list.List;

public class MemoScriptInflater {
    public static MemoScriptType getScriptType(final LockingScript lockingScript) {
        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getCount() < 2) { return null; }

        { // Ensure the first opcode is OP_RETURN...
            final Operation operation = operations.get(0);
            final boolean firstOperationIsReturn = (operation.getOpcodeByte() == Opcode.RETURN.getValue());
            if (! firstOperationIsReturn) { return null; }
        }

        { // Ensure the first opcode after the OP_RETURN is a push and that its value matches a valid Memo Operation...
            final Operation operation = operations.get(1);
            if (operation.getType() != Operation.Type.OP_PUSH) { return null; }
            final PushOperation pushOperation = (PushOperation) operation;

            final Value value = pushOperation.getValue();
            return MemoScriptType.fromBytes(value);
        }
    }
}

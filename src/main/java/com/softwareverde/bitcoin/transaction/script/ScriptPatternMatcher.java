package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;

public class ScriptPatternMatcher {
    protected static final List<Operation.Opcode> PAY_TO_PUBLIC_KEY_HASH_PATTERN;
    static {
        final ImmutableListBuilder<Operation.Opcode> listBuilder = new ImmutableListBuilder<Operation.Opcode>(3);

        listBuilder.add(Operation.Opcode.COPY_1ST);
        listBuilder.add(Operation.Opcode.SHA_256_THEN_RIPEMD_160);
        listBuilder.add(Operation.Opcode.PUSH_DATA);
        listBuilder.add(Operation.Opcode.IS_EQUAL_THEN_VERIFY);
        listBuilder.add(Operation.Opcode.CHECK_SIGNATURE);

        PAY_TO_PUBLIC_KEY_HASH_PATTERN = listBuilder.build();
    }

    protected static final List<Operation.Opcode> PAY_TO_SCRIPT_HASH_PATTERN;
    static {
        final ImmutableListBuilder<Operation.Opcode> listBuilder = new ImmutableListBuilder<Operation.Opcode>(3);

        listBuilder.add(Operation.Opcode.SHA_256_THEN_RIPEMD_160);
        listBuilder.add(Operation.Opcode.PUSH_DATA);
        listBuilder.add(Operation.Opcode.IS_EQUAL);

        PAY_TO_SCRIPT_HASH_PATTERN = listBuilder.build();
    }

    protected Boolean _matchesPattern(final List<Operation.Opcode> pattern, final List<Operation> scriptOperations) {
        final int opcodeCount = pattern.getSize();
        final int operationCount = scriptOperations.getSize();

        if (opcodeCount != operationCount) { return false; }

        for (int i = 0; i < opcodeCount; ++i) {
            final Operation.Opcode opcode = pattern.get(i);
            final Operation operation = scriptOperations.get(i);

            final boolean isMatch = (opcode.matchesByte(operation.getOpcodeByte()));
            if (! isMatch) { return false; }
        }

        return true;
    }

    /**
     * Returns true if the provided script matches the Pay-To-Public-Key-Hash (P2PKH) script format.
     *  The P2SH format is: OP_DUP OP_HASH160 <20-byte public-key-hash> OP_EQUALVERIFY OP_CHECKSIG
     */
    public Boolean matchesPayToPublicKeyHashFormat(final Script unlockingScript) {

        final List<Operation> scriptOperations = unlockingScript.getOperations();
        final boolean matchesPattern = _matchesPattern(PAY_TO_PUBLIC_KEY_HASH_PATTERN, scriptOperations);
        if (! matchesPattern) { return false; }

        final Operation pushOperation = scriptOperations.get(2);
        if (pushOperation instanceof PushOperation) {
            final int pushedByteCount = ((PushOperation) pushOperation).getValue().getByteCount();
            if (pushedByteCount != 20) { return false; }
        }
        else { return false; }

        return true;
    }

    /**
     * Returns true if the provided script matches the Pay-To-Script-Hash (P2SH) script format.
     *  The P2SH format is: HASH160 <20-byte redeem-script-hash> EQUAL
     */
    public Boolean matchesPayToScriptHashFormat(final Script unlockingScript) {

        final List<Operation> scriptOperations = unlockingScript.getOperations();
        final boolean matchesPattern = _matchesPattern(PAY_TO_SCRIPT_HASH_PATTERN, scriptOperations);
        if (! matchesPattern) { return false; }

        final Operation pushOperation = scriptOperations.get(1);
        if (pushOperation instanceof PushOperation) {
            final int pushedByteCount = ((PushOperation) pushOperation).getValue().getByteCount();
            if (pushedByteCount != 20) { return false; }
        }
        else { return false; }

        return true;
    }
}

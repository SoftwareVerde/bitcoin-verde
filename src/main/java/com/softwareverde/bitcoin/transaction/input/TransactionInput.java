package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.Constable;

public interface TransactionInput extends Constable<ImmutableTransactionInput> {
    Long MAX_SEQUENCE_NUMBER = 0xFFFFFFFFL;

    static TransactionInput createCoinbaseTransactionInput(final String data) {
        final UnlockingScript unlockingScript;
        { // Initialize unlockingScript...
            final ScriptBuilder scriptBuilder = new ScriptBuilder();
            scriptBuilder.pushString(data);
            unlockingScript = scriptBuilder.buildUnlockingScript();
        }

        final MutableTransactionInput coinbaseTransactionInput = new MutableTransactionInput();
        coinbaseTransactionInput.setUnlockingScript(unlockingScript);
        return coinbaseTransactionInput;
    }

    Hash getPreviousOutputTransactionHash();
    Integer getPreviousOutputIndex();
    Script getUnlockingScript();
    Long getSequenceNumber();

    @Override
    ImmutableTransactionInput asConst();
}

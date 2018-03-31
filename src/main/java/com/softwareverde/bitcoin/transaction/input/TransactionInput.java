package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.MutableByteArray;

public interface TransactionInput extends Constable<ImmutableTransactionInput> {
    Long MAX_SEQUENCE_NUMBER = 0xFFFFFFFFL;

    static TransactionInput createCoinbaseTransactionInput(final String coinbaseMessage) {
        final UnlockingScript unlockingScript;
        { // Initialize unlockingScript...
            final ScriptBuilder scriptBuilder = new ScriptBuilder();
            scriptBuilder.pushString(coinbaseMessage);
            unlockingScript = scriptBuilder.buildUnlockingScript();
        }

        final MutableTransactionInput coinbaseTransactionInput = new MutableTransactionInput();
        coinbaseTransactionInput.setUnlockingScript(unlockingScript);
        return coinbaseTransactionInput;
    }

    static TransactionInput createCoinbaseTransactionInputWithExtraNonce(final String coinbaseMessage, final Integer extraNonceByteCount) {
        final UnlockingScript unlockingScript;
        { // Initialize unlockingScript...
            final ScriptBuilder scriptBuilder = new ScriptBuilder();
            scriptBuilder.pushString(coinbaseMessage);
            scriptBuilder.pushBytes(new MutableByteArray(extraNonceByteCount)); // Pad the coinbaseTransactionInput with extraNonceByteCount bytes. (Which adds the appropriate opcode in addition to extraNonceByteCount bytes...)
            unlockingScript = scriptBuilder.buildUnlockingScript();
        }

        final MutableTransactionInput coinbaseTransactionInput = new MutableTransactionInput();
        coinbaseTransactionInput.setUnlockingScript(unlockingScript);
        return coinbaseTransactionInput;
    }

    Hash getPreviousOutputTransactionHash();
    Integer getPreviousOutputIndex();
    UnlockingScript getUnlockingScript();
    Long getSequenceNumber();

    @Override
    ImmutableTransactionInput asConst();
}

package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Jsonable;

public interface TransactionInput extends Constable<ImmutableTransactionInput>, Jsonable {
    /**
     * Creates a coinbase transaction with the provided blockHeight and coinbaseMessage as the contents of the TransactionInput's UnlockingScript.
     *  The blockHeight and coinbaseMessage are transformed into PushOperations for the UnlockingScript.
     *  If blockHeight is provided (it may be null), the blockHeight will be the first value pushed onto the UnlockingScript (as per Bip34).
     */
    static TransactionInput createCoinbaseTransactionInput(final Long blockHeight, final String coinbaseMessage) {
        final UnlockingScript unlockingScript;
        { // Initialize unlockingScript...
            final ScriptBuilder scriptBuilder = new ScriptBuilder();
            if (blockHeight != null) {
                scriptBuilder.pushInteger(blockHeight);
            }
            scriptBuilder.pushString(coinbaseMessage);
            unlockingScript = scriptBuilder.buildUnlockingScript();
        }

        final MutableTransactionInput coinbaseTransactionInput = new MutableTransactionInput();
        coinbaseTransactionInput.setUnlockingScript(unlockingScript);
        return coinbaseTransactionInput;
    }

    /**
     * Creates a coinbase transaction with the provided blockHeight and coinbaseMessage as the contents of the TransactionInput's UnlockingScript.
     *  This function is nearly identical to TransactionInput.createCoinbaseTransactionInput(), except an additional PushOperation is added to the end of the UnlockingScript.
     *  This value is all zeroes, and is extraNonceByteCount bytes long.
     */
    static TransactionInput createCoinbaseTransactionInputWithExtraNonce(final Long blockHeight, final String coinbaseMessage, final Integer extraNonceByteCount) {
        final UnlockingScript unlockingScript;
        { // Initialize unlockingScript...
            final ScriptBuilder scriptBuilder = new ScriptBuilder();
            if (blockHeight != null) {
                scriptBuilder.pushInteger(blockHeight);
            }
            scriptBuilder.pushString(coinbaseMessage);
            scriptBuilder.pushBytes(new MutableByteArray(extraNonceByteCount)); // Pad the coinbaseTransactionInput with extraNonceByteCount bytes. (Which adds the appropriate opcode in addition to extraNonceByteCount bytes...)
            unlockingScript = scriptBuilder.buildUnlockingScript();
        }

        final MutableTransactionInput coinbaseTransactionInput = new MutableTransactionInput();
        coinbaseTransactionInput.setUnlockingScript(unlockingScript);
        return coinbaseTransactionInput;
    }

    Sha256Hash getPreviousOutputTransactionHash();
    Integer getPreviousOutputIndex();
    UnlockingScript getUnlockingScript();
    SequenceNumber getSequenceNumber();

    @Override
    ImmutableTransactionInput asConst();
}

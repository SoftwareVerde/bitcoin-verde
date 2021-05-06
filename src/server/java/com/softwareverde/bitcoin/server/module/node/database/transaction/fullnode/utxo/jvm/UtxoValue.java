package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm;

public class UtxoValue {

    public static final long UNKNOWN_BLOCK_HEIGHT = -1L; // Sentinel value for Utxo's with an unknown block height; this state is only possible if the UTXO has been spent.
    public static final long SPENT_AMOUNT = -1L;

    public final int spentStateCode;
    public final long blockHeight;
    public final long amount;
    public final byte[] lockingScript;

    public UtxoValue(final JvmSpentState jvmSpentState, final long blockHeight, final Object dummy) {
        this(jvmSpentState.intValue(), blockHeight, SPENT_AMOUNT, null);
    }

    public UtxoValue(final JvmSpentState jvmSpentState, final long blockHeight, final long amount, final byte[] lockingScript) {
        this(jvmSpentState.intValue(), blockHeight, amount, lockingScript);
    }

    public UtxoValue(final int spentStateCode, final long blockHeight, final long amount, final byte[] lockingScript) {
        this.spentStateCode = spentStateCode;
        this.blockHeight = blockHeight;
        this.amount = amount;
        this.lockingScript = lockingScript;
    }

    public JvmSpentState getSpentState() {
        return new JvmSpentState(this.spentStateCode);
    }
}
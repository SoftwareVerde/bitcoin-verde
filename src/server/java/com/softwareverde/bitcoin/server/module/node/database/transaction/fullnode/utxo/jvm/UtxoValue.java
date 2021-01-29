package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm;

public class UtxoValue {
    public final int spentStateCode;
    public final long blockHeight;

    public UtxoValue(final JvmSpentState jvmSpentState, final long blockHeight) {
        this.spentStateCode = jvmSpentState.intValue();
        this.blockHeight = blockHeight;
    }

    public UtxoValue(final int spentStateCode, final long blockHeight) {
        this.spentStateCode = spentStateCode;
        this.blockHeight = blockHeight;
    }

    public JvmSpentState getSpentState() {
        return new JvmSpentState(this.spentStateCode);
    }
}
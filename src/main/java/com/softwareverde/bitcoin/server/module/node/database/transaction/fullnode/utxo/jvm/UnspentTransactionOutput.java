package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm;

import java.util.Comparator;

public class UnspentTransactionOutput {
    public static final Comparator<UnspentTransactionOutput> COMPARATOR = new Comparator<UnspentTransactionOutput>() {
        @Override
        public int compare(final UnspentTransactionOutput o1, final UnspentTransactionOutput o2) {
            return o1._utxoKey.compareTo(o2._utxoKey);
        }
    };

    protected final UtxoKey _utxoKey;
    protected final UtxoValue _utxoValue;

    public UnspentTransactionOutput(final UtxoKey utxoKey, final UtxoValue utxoValue) {
        _utxoKey = utxoKey;
        _utxoValue = utxoValue;
    }

    public byte[] getTransactionHash() {
        return _utxoKey.transactionHash;
    }

    public int getOutputIndex() {
        return _utxoKey.outputIndex;
    }

    public long getBlockHeight() {
        return _utxoValue.blockHeight;
    }
}

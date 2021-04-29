package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm;

import java.util.Comparator;

public class Utxo {
    public static final Comparator<Utxo> COMPARATOR = new Comparator<Utxo>() {
        @Override
        public int compare(final Utxo o1, final Utxo o2) {
            return o1._utxoKey.compareTo(o2._utxoKey);
        }
    };

    protected final UtxoKey _utxoKey;
    protected final UtxoValue _utxoValue;

    public Utxo(final UtxoKey utxoKey, final UtxoValue utxoValue) {
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

    public long getAmount() {
        return _utxoValue.amount;
    }

    public byte[] getLockingScript() {
        return _utxoValue.lockingScript;
    }
}

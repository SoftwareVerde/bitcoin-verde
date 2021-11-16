package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm;

import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ByteArrayCore;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.Comparator;

public class UtxoKey implements Comparable<UtxoKey> {
    public static final Comparator<UtxoKey> COMPARATOR = new Comparator<UtxoKey>() {
        @Override
        public int compare(final UtxoKey utxo0, final UtxoKey utxo1) {
            if (utxo0.transactionHash != utxo1.transactionHash) {
                for (int i = 0; i < Sha256Hash.BYTE_COUNT; ++i) {
                    final byte b0 = utxo0.transactionHash[i];
                    final byte b1 = utxo1.transactionHash[i];

                    final int compare = ByteUtil.compare(b0, b1);
                    if (compare != 0) { return compare; }
                }
            }

            return Integer.compare(utxo0.outputIndex, utxo1.outputIndex);
        }
    };

    public final byte[] transactionHash;
    public final int outputIndex;

    public UtxoKey(final TransactionOutputIdentifier transactionOutputIdentifier) {
        this(
            transactionOutputIdentifier.getTransactionHash().getBytes(),
            transactionOutputIdentifier.getOutputIndex()
        );
    }

    public UtxoKey(final byte[] transactionHash, final int outputIndex) {
        this.transactionHash = transactionHash;
        this.outputIndex = outputIndex;
    }

    @Override
    public boolean equals(final Object obj) {
        if (! (obj instanceof UtxoKey)) { return false; }

        return (COMPARATOR.compare(this, (UtxoKey) obj) == 0);
    }

    @Override
    public int compareTo(final UtxoKey utxo) {
        return COMPARATOR.compare(this, utxo);
    }

    @Override
    public int hashCode() {
        return ByteArrayCore.hashCode(ByteArray.wrap(this.transactionHash)) + Integer.hashCode(this.outputIndex);
    }
}
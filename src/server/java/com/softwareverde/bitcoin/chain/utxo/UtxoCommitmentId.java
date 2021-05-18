package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.util.type.identifier.Identifier;

public class UtxoCommitmentId extends Identifier {
    public static UtxoCommitmentId wrap(final Long value) {
        if (value == null) { return null; }
        return new UtxoCommitmentId(value);
    }

    protected UtxoCommitmentId(final Long value) {
        super(value);
    }
}

package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofInflater;

public interface DoubleSpendProofInflaters extends Inflater {
    DoubleSpendProofInflater getDoubleSpendProofInflater();
}

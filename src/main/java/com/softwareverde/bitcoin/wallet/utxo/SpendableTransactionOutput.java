package com.softwareverde.bitcoin.wallet.utxo;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.Constable;
import com.softwareverde.util.Util;

import java.util.Comparator;

public interface SpendableTransactionOutput extends Constable<ImmutableSpendableTransactionOutput> {
    Comparator<SpendableTransactionOutput> AMOUNT_ASCENDING_COMPARATOR = new Comparator<SpendableTransactionOutput>() {
        @Override
        public int compare(final SpendableTransactionOutput transactionOutput0, final SpendableTransactionOutput transactionOutput1) {
            return transactionOutput0.getTransactionOutput().getAmount().compareTo(transactionOutput1.getTransactionOutput().getAmount());
        }
    };

    TransactionOutputIdentifier getIdentifier();
    TransactionOutput getTransactionOutput();

    Boolean isSpent();

    @Override
    ImmutableSpendableTransactionOutput asConst();
}

abstract class SpendableTransactionOutputCore implements SpendableTransactionOutput {
    @Override
    public int hashCode() {
        return (this.getIdentifier().hashCode() + this.getTransactionOutput().hashCode() + this.isSpent().hashCode());
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof SpendableTransactionOutput)) { return false; }

        final SpendableTransactionOutput spendableTransactionOutput = (SpendableTransactionOutput) object;

        if (! Util.areEqual(this.getIdentifier(), spendableTransactionOutput.getIdentifier())) { return false; }
        if (! Util.areEqual(this.getTransactionOutput(), spendableTransactionOutput.getTransactionOutput())) { return false; }
        if (! Util.areEqual(this.isSpent(), spendableTransactionOutput.isSpent())) { return false; }

        return true;
    }

    @Override
    public String toString() {
        return (this.getIdentifier().toString() + "=" + this.getTransactionOutput().getAmount() + (this.isSpent() ? " (SPENT)" : ""));
    }
}
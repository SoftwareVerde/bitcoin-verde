package com.softwareverde.bitcoin.wallet.utxo;

import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;

public class MutableSpendableTransactionOutput extends SpendableTransactionOutputCore implements SpendableTransactionOutput {
    protected final TransactionOutputIdentifier _transactionOutputIdentifier;
    protected final TransactionOutput _transactionOutput;

    protected Boolean _isSpent;

    public MutableSpendableTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput) {
        _transactionOutputIdentifier = transactionOutputIdentifier;
        _transactionOutput = transactionOutput.asConst();
        _isSpent = false;
    }

    public MutableSpendableTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput, final Boolean isSpent) {
        _transactionOutputIdentifier = transactionOutputIdentifier;
        _transactionOutput = transactionOutput.asConst();
        _isSpent = isSpent;
    }

    public MutableSpendableTransactionOutput(final SpendableTransactionOutput spendableTransactionOutput) {
        _transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
        _transactionOutput = spendableTransactionOutput.getTransactionOutput().asConst();
        _isSpent = spendableTransactionOutput.isSpent();
    }

    @Override
    public TransactionOutputIdentifier getIdentifier() {
        return _transactionOutputIdentifier;
    }

    @Override
    public TransactionOutput getTransactionOutput() {
        return _transactionOutput;
    }

    public void setIsSpent(final Boolean isSpent) {
        _isSpent = isSpent;
    }

    @Override
    public Boolean isSpent() {
        return _isSpent;
    }

    @Override
    public ImmutableSpendableTransactionOutput asConst() {
        return new ImmutableSpendableTransactionOutput(this);
    }
}

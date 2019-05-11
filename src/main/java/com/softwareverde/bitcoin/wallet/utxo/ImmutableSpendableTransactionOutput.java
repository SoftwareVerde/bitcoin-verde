package com.softwareverde.bitcoin.wallet.utxo;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.Const;

public class ImmutableSpendableTransactionOutput extends SpendableTransactionOutputCore implements SpendableTransactionOutput, Const {
    protected final Address _address;
    protected final TransactionOutputIdentifier _transactionOutputIdentifier;
    protected final TransactionOutput _transactionOutput;
    protected final Boolean _isSpent;

    public ImmutableSpendableTransactionOutput(final Address address, final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput, final Boolean isSpent) {
        _address = (address != null ? address.asConst() : null);
        _transactionOutputIdentifier = transactionOutputIdentifier;
        _transactionOutput = transactionOutput.asConst();
        _isSpent = isSpent;
    }

    public ImmutableSpendableTransactionOutput(final SpendableTransactionOutput spendableTransactionOutput) {
        final Address address = spendableTransactionOutput.getAddress();

        _address = (address != null ? address.asConst() : null);
        _transactionOutputIdentifier = spendableTransactionOutput.getIdentifier();
        _transactionOutput = spendableTransactionOutput.getTransactionOutput().asConst();
        _isSpent = spendableTransactionOutput.isSpent();
    }

    @Override
    public Address getAddress() {
        return _address;
    }

    @Override
    public TransactionOutputIdentifier getIdentifier() {
        return _transactionOutputIdentifier;
    }

    @Override
    public TransactionOutput getTransactionOutput() {
        return _transactionOutput;
    }

    @Override
    public Boolean isSpent() {
        return _isSpent;
    }

    @Override
    public ImmutableSpendableTransactionOutput asConst() {
        return this;
    }
}

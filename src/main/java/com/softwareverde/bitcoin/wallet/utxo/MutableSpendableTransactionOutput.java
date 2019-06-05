package com.softwareverde.bitcoin.wallet.utxo;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;

public class MutableSpendableTransactionOutput extends SpendableTransactionOutputCore implements SpendableTransactionOutput {
    protected final Address _address;
    protected final TransactionOutputIdentifier _transactionOutputIdentifier;
    protected final TransactionOutput _transactionOutput;

    protected Boolean _isSpent;

    public MutableSpendableTransactionOutput(final Address address, final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput) {
        _address = (address != null ? address.asConst() : null);
        _transactionOutputIdentifier = transactionOutputIdentifier;
        _transactionOutput = transactionOutput.asConst();
        _isSpent = false;
    }

    public MutableSpendableTransactionOutput(final Address address, final TransactionOutputIdentifier transactionOutputIdentifier, final TransactionOutput transactionOutput, final Boolean isSpent) {
        _address = (address != null ? address.asConst() : null);
        _transactionOutputIdentifier = transactionOutputIdentifier;
        _transactionOutput = transactionOutput.asConst();
        _isSpent = isSpent;
    }

    public MutableSpendableTransactionOutput(final SpendableTransactionOutput spendableTransactionOutput) {
        _address = spendableTransactionOutput.getAddress();
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

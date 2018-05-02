package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.signature.HashType;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class SignatureContext {
    private final Transaction _transaction;
    private final HashType _hashType;

    private final MutableList<Boolean> _inputIndexesToSign = new MutableList<Boolean>();
    private final MutableList<TransactionOutput> _previousTransactionOutputsBeingSpent = new MutableList<TransactionOutput>();
    private final MutableList<Integer> _codeSeparatorIndexes = new MutableList<Integer>();

    private final MutableList<Boolean> _outputIndexesToSign = new MutableList<Boolean>();

    private Integer _inputIndexBeingSigned = null;
    private Script _currentScript;

    public SignatureContext(final Transaction transaction, final HashType hashType) {
        _transaction = transaction;
        _hashType = hashType;

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i = 0; i < transactionInputs.getSize(); ++i) {
            _inputIndexesToSign.add(false); // All inputs are not signed by default...
            _previousTransactionOutputsBeingSpent.add(null);
            _codeSeparatorIndexes.add(0);
        }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        for (int i = 0; i < transactionOutputs.getSize(); ++i) {
            _outputIndexesToSign.add(true); // All outputs are signed by default...
        }
    }

    public void setShouldSignInput(final Integer index, final Boolean shouldSignInput, final TransactionOutput outputBeingSpent) {
        _inputIndexesToSign.set(index, shouldSignInput);
        _previousTransactionOutputsBeingSpent.set(index, outputBeingSpent);
        _codeSeparatorIndexes.set(index, 0);
    }

    public void setShouldSignOutput(final Integer index, final Boolean shouldSignOutput) {
        _outputIndexesToSign.set(index, shouldSignOutput);
    }

    public void setLastCodeSeparatorIndex(final Integer index, final Integer lastCodeSeparatorIndex) {
        _codeSeparatorIndexes.set(index, lastCodeSeparatorIndex);
    }

    public void setInputIndexBeingSigned(final Integer inputIndex) {
        _inputIndexBeingSigned = inputIndex;
    }

    public void setCurrentScript(final Script script) {
        _currentScript = script;
    }

    public Transaction getTransaction() {
        return _transaction;
    }

    public HashType getHashType() {
        return _hashType;
    }

    public Boolean shouldInputIndexBeSigned(final Integer inputIndex) {
        return _inputIndexesToSign.get(inputIndex);
    }

    public Boolean shouldOutputIndexBeSigned(final Integer outputIndex) {
        return _outputIndexesToSign.get(outputIndex);
    }

    public TransactionOutput getTransactionOutputBeingSpent(final Integer index) {
        return _previousTransactionOutputsBeingSpent.get(index);
    }

    public Integer getLastCodeSeparatorIndex(final Integer index) {
        return _codeSeparatorIndexes.get(index);
    }

    public Integer getInputIndexBeingSigned() {
        return _inputIndexBeingSigned;
    }

    public Script getCurrentScript() {
        return _currentScript;
    }
}

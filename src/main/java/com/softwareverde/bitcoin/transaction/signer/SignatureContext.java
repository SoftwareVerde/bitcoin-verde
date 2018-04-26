package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class SignatureContext {
    private final Transaction _transaction;
    private final ScriptSignature.HashType _hashType;

    private final MutableList<Boolean> _inputIndexesToSign = new MutableList<Boolean>();
    private final MutableList<TransactionOutput> _previousTransactionOutputsBeingSpent = new MutableList<TransactionOutput>();
    private final MutableList<Integer> _codeSeparatorIndexes = new MutableList<Integer>();

    private final MutableList<Boolean> _outputIndexesToSign = new MutableList<Boolean>();

    private Script _currentScript;

    public SignatureContext(final Transaction transaction, final ScriptSignature.HashType hashType) {
        _transaction = transaction;
        _hashType = hashType;

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i = 0; i < transactionInputs.getSize(); ++i) {
            _inputIndexesToSign.add(false);
            _previousTransactionOutputsBeingSpent.add(null);
            _codeSeparatorIndexes.add(0);
        }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        for (int i = 0; i < transactionOutputs.getSize(); ++i) {
            _outputIndexesToSign.add(false);
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

    public void setCurrentScript(final Script script) {
        _currentScript = script;
    }

    public Transaction getTransaction() {
        return _transaction;
    }

    public ScriptSignature.HashType getHashType() {
        return _hashType;
    }

    public Boolean shouldInputIndexBeSigned(final Integer index) {
        return _inputIndexesToSign.get(index);
    }

    public Boolean shouldOutputIndexBeSigned(final Integer index) {
        return _outputIndexesToSign.get(index);
    }

    public TransactionOutput getTransactionOutputBeingSpent(final Integer index) {
        return _previousTransactionOutputsBeingSpent.get(index);
    }

    public Integer getLastCodeSeparatorIndex(final Integer index) {
        return _codeSeparatorIndexes.get(index);
    }

    public Script getCurrentScript() {
        return _currentScript;
    }
}

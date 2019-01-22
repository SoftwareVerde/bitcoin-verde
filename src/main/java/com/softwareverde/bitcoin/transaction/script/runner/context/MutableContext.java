package com.softwareverde.bitcoin.transaction.script.runner.context;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;

public class MutableContext implements Context, Const {
    protected Long _blockHeight;
    protected Transaction _transaction;

    protected Integer _transactionInputIndex;
    protected TransactionInput _transactionInput;
    protected TransactionOutput _transactionOutput;

    protected Script _currentScript = null;
    protected Integer _currentScriptIndex = 0;
    protected Integer _scriptLastCodeSeparatorIndex = 0;

    public MutableContext() { }

    public MutableContext(final Context context) {
        _blockHeight = context.getBlockHeight();
        _transaction = ConstUtil.asConstOrNull(context.getTransaction());
        _transactionInputIndex = context.getTransactionInputIndex();
        _transactionInput = ConstUtil.asConstOrNull(context.getTransactionInput());
        _transactionOutput = ConstUtil.asConstOrNull(context.getTransactionOutput());

        final Script currentScript = context.getCurrentScript();
        _currentScript = ConstUtil.asConstOrNull(currentScript);
        _currentScriptIndex = context.getScriptIndex();
        _scriptLastCodeSeparatorIndex = context.getScriptLastCodeSeparatorIndex();
    }

    public void setBlockHeight(final Long blockHeight) {
        _blockHeight = blockHeight;
    }

    /**
     * Sets the Transaction currently being validated.
     */
    public void setTransaction(final Transaction transaction) {
        _transaction = transaction;
    }

    public void setTransactionInputIndex(final Integer transactionInputIndex) {
        _transactionInputIndex = transactionInputIndex;
    }

    public void setTransactionInput(final TransactionInput transactionInput) {
        _transactionInput = transactionInput;
    }

    public void setTransactionOutputBeingSpent(final TransactionOutput transactionOutput) {
        _transactionOutput = transactionOutput;
    }

    public void setCurrentScript(final Script script) {
        _currentScript = script;
        _currentScriptIndex = 0;
        _scriptLastCodeSeparatorIndex = 0;
    }

    public void incrementCurrentScriptIndex() {
        _currentScriptIndex += 1;
    }

    public void setCurrentScriptLastCodeSeparatorIndex(final Integer codeSeparatorIndex) {
        _scriptLastCodeSeparatorIndex = codeSeparatorIndex;
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    @Override
    public TransactionInput getTransactionInput() {
        return _transactionInput;
    }

    @Override
    public TransactionOutput getTransactionOutput() {
        return _transactionOutput;
    }

    @Override
    public Transaction getTransaction() {
        return _transaction;
    }

    @Override
    public Integer getTransactionInputIndex() {
        return _transactionInputIndex;
    }

    @Override
    public Script getCurrentScript() {
        return _currentScript;
    }

    @Override
    public Integer getScriptIndex() {
        return _currentScriptIndex;
    }

    @Override
    public Integer getScriptLastCodeSeparatorIndex() {
        return _scriptLastCodeSeparatorIndex;
    }

    @Override
    public ImmutableContext asConst() {
        return new ImmutableContext(this);
    }

    @Override
    public Json toJson() {
        final ContextDeflater contextDeflater = new ContextDeflater();
        return contextDeflater.toJson(this);
    }
}

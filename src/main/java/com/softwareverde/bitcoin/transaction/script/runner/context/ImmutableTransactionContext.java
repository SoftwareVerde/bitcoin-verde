package com.softwareverde.bitcoin.transaction.script.runner.context;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;

public class ImmutableTransactionContext implements TransactionContext, Const {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final Long _blockHeight;
    protected final MedianBlockTime _medianBlockTime;
    protected final Transaction _transaction;

    protected final Integer _transactionInputIndex;
    protected final TransactionInput _transactionInput;
    protected final TransactionOutput _transactionOutput;

    protected final Script _currentScript;
    protected final Integer _currentScriptIndex;
    protected final Integer _scriptLastCodeSeparatorIndex;
    protected final Integer _signatureOperationCount;

    public ImmutableTransactionContext(final TransactionContext transactionContext) {
        _upgradeSchedule = transactionContext.getUpgradeSchedule();
        _blockHeight = transactionContext.getBlockHeight();
        _medianBlockTime = ConstUtil.asConstOrNull(transactionContext.getMedianBlockTime());
        _transaction = ConstUtil.asConstOrNull(transactionContext.getTransaction());
        _transactionInputIndex = transactionContext.getTransactionInputIndex();
        _transactionInput = ConstUtil.asConstOrNull(transactionContext.getTransactionInput());
        _transactionOutput = ConstUtil.asConstOrNull(transactionContext.getTransactionOutput());

        final Script currentScript = transactionContext.getCurrentScript();
        _currentScript = (currentScript != null ? ConstUtil.asConstOrNull(currentScript) : null);
        _currentScriptIndex = transactionContext.getScriptIndex();
        _scriptLastCodeSeparatorIndex = transactionContext.getScriptLastCodeSeparatorIndex();

        _signatureOperationCount = transactionContext.getSignatureOperationCount();
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    @Override
    public MedianBlockTime getMedianBlockTime() {
        return _medianBlockTime;
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
    public Integer getSignatureOperationCount() {
        return _signatureOperationCount;
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }

    @Override
    public ImmutableTransactionContext asConst() {
        return this;
    }

    @Override
    public Json toJson() {
        final ContextDeflater contextDeflater = new ContextDeflater();
        return contextDeflater.toJson(this);
    }
}

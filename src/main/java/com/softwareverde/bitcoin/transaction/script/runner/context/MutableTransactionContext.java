package com.softwareverde.bitcoin.transaction.script.runner.context;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class MutableTransactionContext implements TransactionContext {
    public static MutableTransactionContext getContextForVerification(final Transaction signedTransaction, final Integer transactionInputIndex, final TransactionOutput transactionOutputBeingSpent, final UpgradeSchedule upgradeSchedule, final List<TransactionOutput> previousTransactionOutputs) {
        return MutableTransactionContext.getContextForVerification(signedTransaction, transactionInputIndex, transactionOutputBeingSpent, MedianBlockTime.MAX_VALUE, upgradeSchedule, previousTransactionOutputs);
    }

    public static MutableTransactionContext getContextForVerification(final Transaction signedTransaction, final Integer transactionInputIndex, final TransactionOutput transactionOutputBeingSpent, final MedianBlockTime medianBlockTime, final UpgradeSchedule upgradeSchedule, final List<TransactionOutput> previousTransactionOutputs) {
        final List<TransactionInput> signedTransactionInputs = signedTransaction.getTransactionInputs();
        final TransactionInput signedTransactionInput = signedTransactionInputs.get(transactionInputIndex);

        final MutableTransactionContext mutableContext = new MutableTransactionContext(upgradeSchedule);
        mutableContext.setCurrentScript(null);
        mutableContext.setTransactionInputIndex(transactionInputIndex);
        mutableContext.setTransactionInput(signedTransactionInput);
        mutableContext.setTransaction(signedTransaction);
        mutableContext.setBlockHeight(Long.MAX_VALUE);
        mutableContext.setMedianBlockTime(Util.coalesce(medianBlockTime, MedianBlockTime.MAX_VALUE));
        mutableContext.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
        mutableContext.setCurrentScriptLastCodeSeparatorIndex(0);
        mutableContext.setPreviousTransactionOutputs(previousTransactionOutputs);
        return mutableContext;
    }

    protected final UpgradeSchedule _upgradeSchedule;
    protected final TransactionSigner _transactionSigner;

    protected Long _blockHeight;
    protected MedianBlockTime _medianBlockTime;
    protected Transaction _transaction;

    protected Integer _transactionInputIndex;
    protected TransactionInput _transactionInput;
    protected TransactionOutput _transactionOutputBeingSpent;
    protected final MutableList<TransactionOutput> _previousTransactionOutputs = new MutableList<>(0);

    protected Script _currentScript = null;
    protected Integer _currentScriptIndex = 0;
    protected Integer _scriptLastCodeSeparatorIndex = 0;
    protected Integer _signatureOperationCount = 0;
    protected Integer _operationCount = 0;

    public MutableTransactionContext(final UpgradeSchedule upgradeSchedule) {
        this(upgradeSchedule, new TransactionSigner());
    }

    public MutableTransactionContext(final UpgradeSchedule upgradeSchedule, final TransactionSigner transactionSigner) {
        _upgradeSchedule = upgradeSchedule;
        _transactionSigner = transactionSigner;
    }

    public MutableTransactionContext(final TransactionContext transactionContext) {
        _upgradeSchedule = transactionContext.getUpgradeSchedule();
        _transactionSigner = transactionContext.getTransactionSigner();
        _blockHeight = transactionContext.getBlockHeight();
        _medianBlockTime = transactionContext.getMedianBlockTime();
        _transaction = ConstUtil.asConstOrNull(transactionContext.getTransaction());
        _transactionInputIndex = transactionContext.getTransactionInputIndex();
        _transactionInput = ConstUtil.asConstOrNull(transactionContext.getTransactionInput());
        _transactionOutputBeingSpent = ConstUtil.asConstOrNull(transactionContext.getTransactionOutputBeingSpent());

        final List<TransactionOutput> transactionOutputs = transactionContext.getPreviousTransactionOutputs();
        if (transactionOutputs != null) {
            _previousTransactionOutputs.addAll(transactionOutputs);
        }

        final Script currentScript = transactionContext.getCurrentScript();
        _currentScript = ConstUtil.asConstOrNull(currentScript);
        _currentScriptIndex = transactionContext.getScriptIndex();
        _scriptLastCodeSeparatorIndex = transactionContext.getScriptLastCodeSeparatorIndex();

        _signatureOperationCount = transactionContext.getSignatureOperationCount();
        _operationCount = transactionContext.getOperationCount();
    }

    public void setBlockHeight(final Long blockHeight) {
        _blockHeight = blockHeight;
    }

    public void setMedianBlockTime(final MedianBlockTime medianBlockTime) {
        _medianBlockTime = medianBlockTime;
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
        _transactionOutputBeingSpent = transactionOutput;
    }

    public void setPreviousTransactionOutputs(final List<TransactionOutput> transactionOutputs) {
        _previousTransactionOutputs.clear();
        _previousTransactionOutputs.addAll(transactionOutputs);
    }

    public void setCurrentScript(final Script script) {
        _currentScript = script;
        _currentScriptIndex = 0;
        _scriptLastCodeSeparatorIndex = 0;
        _operationCount = 0;
    }

    public void incrementCurrentScriptIndex() {
        _currentScriptIndex += 1;
    }

    public void setCurrentScriptLastCodeSeparatorIndex(final Integer codeSeparatorIndex) {
        _scriptLastCodeSeparatorIndex = codeSeparatorIndex;
    }

    public void clearSignatureOperationCount() {
        _signatureOperationCount = 0;
    }

    public void incrementSignatureOperationCount(final Integer operationCount) {
        _signatureOperationCount += operationCount;
    }

    public void incrementOperationCount(final Integer operationCount) {
        _operationCount += operationCount;
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
    public TransactionOutput getTransactionOutputBeingSpent() {
        return _transactionOutputBeingSpent;
    }

    @Override
    public TransactionOutput getPreviousTransactionOutput(final Integer outputIndex) {
        if (outputIndex >= _previousTransactionOutputs.getCount()) { return null; }
        return _previousTransactionOutputs.get(outputIndex);
    }

    @Override
    public List<TransactionOutput> getPreviousTransactionOutputs() {
        return _previousTransactionOutputs;
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
    public Integer getOperationCount() {
        return _operationCount;
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }

    @Override
    public TransactionSigner getTransactionSigner() {
        return _transactionSigner;
    }

    @Override
    public ImmutableTransactionContext asConst() {
        return new ImmutableTransactionContext(this);
    }

    @Override
    public Json toJson() {
        final ContextDeflater contextDeflater = new ContextDeflater();
        return contextDeflater.toJson(this);
    }
}

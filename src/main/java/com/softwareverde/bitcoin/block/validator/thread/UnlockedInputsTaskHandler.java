package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public class UnlockedInputsTaskHandler implements TaskHandler<Transaction, Boolean> {
    private final BlockChainSegmentId _blockChainSegmentId;
    private TransactionValidator _transactionValidator;
    private boolean _allInputsAreUnlocked = true;

    public UnlockedInputsTaskHandler(final BlockChainSegmentId blockChainSegmentId) {
        _blockChainSegmentId = blockChainSegmentId;
    }

    @Override
    public void init(final MysqlDatabaseConnection databaseConnection) {
        _transactionValidator = new TransactionValidator(databaseConnection);
    }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _allInputsAreUnlocked) { return; }

        final boolean inputsAreUnlocked = _transactionValidator.validateTransactionInputsAreUnlocked(_blockChainSegmentId, transaction);
        if (! inputsAreUnlocked) {
            _allInputsAreUnlocked = false;
        }
    }

    @Override
    public Boolean getResult() {
        return _allInputsAreUnlocked;
    }
}

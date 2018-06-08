package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.network.NetworkTime;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;

public class UnlockedInputsTaskHandler implements TaskHandler<Transaction, Boolean> {
    private final BlockChainSegmentId _blockChainSegmentId;
    private final NetworkTime _networkTime;
    private TransactionValidator _transactionValidator;
    private boolean _allInputsAreUnlocked = true;

    public UnlockedInputsTaskHandler(final BlockChainSegmentId blockChainSegmentId, final NetworkTime networkTime) {
        _blockChainSegmentId = blockChainSegmentId;
        _networkTime = networkTime.asConst(); // NOTE: This freezes the networkTime...
    }

    @Override
    public void init(final MysqlDatabaseConnection databaseConnection) {
        _transactionValidator = new TransactionValidator(databaseConnection, _networkTime);
    }

    @Override
    public void executeTask(final Transaction transaction) {
        if (! _allInputsAreUnlocked) { return; }

        final boolean transactionInputsAreUnlocked;
        {
            boolean inputsAreUnlocked = false;
            try {
                inputsAreUnlocked = _transactionValidator.validateTransactionInputsAreUnlocked(_blockChainSegmentId, transaction);
            }
            catch (final Exception exception) { Logger.log(exception); }
            transactionInputsAreUnlocked = inputsAreUnlocked;
        }

        if (! transactionInputsAreUnlocked) {
            _allInputsAreUnlocked = false;
        }
    }

    @Override
    public Boolean getResult() {
        return _allInputsAreUnlocked;
    }
}

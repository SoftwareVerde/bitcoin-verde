package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.bip.Bip113;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.network.time.NetworkTime;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

public class TransactionValidator {
    protected final BlockChainDatabaseManager _blockChainDatabaseManager;
    protected final TransactionDatabaseManager _transactionDatabaseManager;
    protected final TransactionOutputDatabaseManager _transactionOutputDatabaseManager;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;

    protected TransactionOutput _findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
            final TransactionId transactionId = _transactionDatabaseManager.getTransactionIdFromHash(transactionOutputIdentifier.getBlockChainSegmentId(), transactionOutputIdentifier.getTransactionHash());
            if (transactionId == null) { return null; }

            final TransactionOutputId transactionOutputId = _transactionOutputDatabaseManager.findTransactionOutput(transactionId, transactionOutputIndex);
            if (transactionOutputId == null) { return null; }

            return _transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }

    public TransactionValidator(final MysqlDatabaseConnection databaseConnection, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public Boolean validateTransactionInputsAreUnlocked(final BlockChainSegmentId blockChainSegmentId, final Transaction transaction) {
        final ScriptRunner scriptRunner = new ScriptRunner();

        final MutableContext context = new MutableContext();

        final Long blockHeight;
        { // Set the block height for this transaction...
            try {
                final BlockChainSegment blockChainSegment = _blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId);
                blockHeight = blockChainSegment.getBlockHeight(); // TODO: This may be insufficient when re-validating previously validated transactions, and may be incorrect due to when reading uncommitted values from the database...
                context.setBlockHeight(blockHeight);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                return false;
            }
        }

        context.setTransaction(transaction);

        { // Validate nLockTime...
            final LockTime lockTime = transaction.getLockTime();
            if (lockTime.getType() == LockTime.Type.BLOCK_HEIGHT) {
                if (blockHeight < lockTime.getMaskedValue()) { return false; }
            }
            else {
                final Long networkTime;
                {
                    if (Bip113.isEnabled(blockHeight)) {
                        networkTime = _medianBlockTime.getCurrentTimeInSeconds();
                    }
                    else {
                        networkTime = _networkTime.getCurrentTimeInSeconds();
                    }
                }

                if (networkTime < lockTime.getMaskedValue()) { return false; }
            }
        }

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int i=0; i<transactionInputs.getSize(); ++i) {
            final TransactionInput transactionInput = transactionInputs.get(i);
            final TransactionOutputIdentifier unspentOutputToSpendIdentifier = new TransactionOutputIdentifier(blockChainSegmentId, transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
            final TransactionOutput outputToSpend = _findTransactionOutput(unspentOutputToSpendIdentifier);
            if (outputToSpend == null) { return false; }

            final LockingScript lockingScript = outputToSpend.getLockingScript();
            final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(outputToSpend);
            context.setTransactionInputIndex(i);

            final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);
            if (! inputIsUnlocked) {
                final TransactionDeflater transactionDeflater = new TransactionDeflater();
                final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
                final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
                Logger.log("\n------------");
                Logger.log("Transaction failed to verify.");
                Logger.log("Tx Hash:\t\t\t" + transaction.getHash() + "_" + context.getTransactionInputIndex());
                Logger.log("Tx Bytes:\t\t" + HexUtil.toHexString(transactionDeflater.toBytes(transaction)));
                Logger.log("Tx Input:\t\t" + HexUtil.toHexString(transactionInputDeflater.toBytes(transactionInput)));
                final TransactionOutput transactionOutput = context.getTransactionOutput();
                Logger.log("Tx Output:\t\t" + transactionOutput.getIndex() + " " + HexUtil.toHexString(transactionOutputDeflater.toBytes(transactionOutput)));
                Logger.log("Block Height:\t\t" + context.getBlockHeight());
                Logger.log("Tx Input Index\t\t" + context.getTransactionInputIndex());
                Logger.log("Locking Script:\t\t" + lockingScript);
                Logger.log("Unlocking Script:\t" + unlockingScript);
                Logger.log("\n------------\n");
                return false;
            }
        }

        return true;
    }
}

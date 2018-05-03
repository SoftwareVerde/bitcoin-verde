package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptDeflater;
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

    public TransactionValidator(final MysqlDatabaseConnection databaseConnection) {
        _blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);
    }

    public Boolean validateTransactionInputsAreUnlocked(final BlockChainSegmentId blockChainSegmentId, final Transaction transaction) {
        final ScriptRunner scriptRunner = new ScriptRunner();

        final MutableContext context = new MutableContext();

        { // Set the block height for this transaction...
            try {
                final BlockChainSegment blockChainSegment = _blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId);
                final Long blockHeight = blockChainSegment.getBlockHeight(); // NOTE: This may be insufficient when re-validating previously validated transactions.
                context.setBlockHeight(blockHeight);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                return false;
            }
        }

        context.setTransaction(transaction);

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

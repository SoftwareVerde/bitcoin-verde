package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.BlockChainId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.Map;

public class BlockValidator {
    protected final BlockDatabaseManager _blockDatabaseManager;
    protected final TransactionValidator _transactionValidator;
    protected final TransactionDatabaseManager _transactionDatabaseManager;
    protected final TransactionOutputDatabaseManager _transactionOutputDatabaseManager;
    protected final TransactionInputDatabaseManager _transactionInputDatabaseManager;

    protected TransactionOutput _findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
            final TransactionId transactionId = _transactionDatabaseManager.getTransactionIdFromHash(transactionOutputIdentifier.getBlockId(), transactionOutputIdentifier.getTransactionHash());
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

    protected Long _calculateTotalTransactionInputs(final BlockId blockId, final Transaction blockTransaction, final List<Transaction> queuedTransactions) {
        final Map<Hash, Transaction> additionalTransactionOutputs = new HashMap<Hash, Transaction>();
        for (final Transaction transaction : queuedTransactions) {
            additionalTransactionOutputs.put(transaction.getHash(), transaction);
        }

        long totalInputValue = 0L;
        final List<TransactionInput> transactionInputs = blockTransaction.getTransactionInputs();
        for (final TransactionInput transactionInput : transactionInputs) {
            final Hash outputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final Integer transactionOutputIndex = transactionInput.getPreviousOutputIndex();

            final TransactionOutput transactionOutput;
            {
                TransactionOutput possibleTransactionOutput = _findTransactionOutput(new TransactionOutputIdentifier(blockId, outputTransactionHash, transactionOutputIndex));
                if (possibleTransactionOutput == null) {
                    final Transaction transactionContainingOutput = additionalTransactionOutputs.get(outputTransactionHash);
                    if (transactionContainingOutput != null) {
                        final List<TransactionOutput> transactionOutputs = transactionContainingOutput.getTransactionOutputs();
                        if (transactionOutputIndex < transactionOutputs.getSize()) {
                            possibleTransactionOutput = transactionOutputs.get(transactionOutputIndex);
                        }
                    }
                }
                transactionOutput = possibleTransactionOutput;
            }
            if (transactionOutput == null) {
                Logger.log("Tx Input, Output Not Found: " + BitcoinUtil.toHexString(outputTransactionHash) + ":" + transactionOutputIndex + ", for Tx: "+ blockTransaction.getHash());
                return null;
            }

            totalInputValue += transactionOutput.getAmount();
        }
        return totalInputValue;
    }

    protected Boolean _validateTransactionExpenditure(final BlockChainId blockChainId, final Transaction blockTransaction, final List<Transaction> queuedTransactions) {
        final Long totalOutputValue = blockTransaction.getTotalOutputValue();
        final Long totalInputValue = _calculateTotalTransactionInputs(blockChainId, blockTransaction, queuedTransactions);
        if (totalInputValue == null) { return false; }

        return (totalOutputValue <= totalInputValue);
    }

    public BlockValidator(final MysqlDatabaseConnection databaseConnection) {
        _blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        _transactionValidator = new TransactionValidator(databaseConnection);
        _transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);
        _transactionInputDatabaseManager = new TransactionInputDatabaseManager(databaseConnection);
    }

    public Boolean validateBlock(final Block block) throws DatabaseException {
        if (! block.isValid()) { return false; }

        final BlockDeflater blockDeflater = new BlockDeflater();

        final BlockId blockId = _blockDatabaseManager.getBlockIdFromHash(block.getHash());
        final BlockChainId blockChainId = _blockDatabaseManager.getBlockChainIdForBlockId(blockId);

        final List<Transaction> blockTransactions = block.getTransactions();
        for (int i=0; i<blockTransactions.getSize(); ++i) {
            if (i == 0) { continue; } // TODO: The coinbase transaction requires a separate validation process...

            final Transaction blockTransaction = blockTransactions.get(i);
            final Boolean transactionExpenditureIsValid = _validateTransactionExpenditure(blockChainId, blockTransaction, blockTransactions);
            if (! transactionExpenditureIsValid) {
                Logger.log("BLOCK VALIDATION: Failed because expenditures did not match.");
                Logger.log(BitcoinUtil.toHexString(blockDeflater.toBytes(block)));
                return false;
            }

            final Boolean transactionInputsAreUnlocked = _transactionValidator.validateTransactionInputsAreUnlocked(blockChainId, blockTransaction);
            if (! transactionInputsAreUnlocked) {
                Logger.log("BLOCK VALIDATION: Failed because of invalid transaction.");
                Logger.log(BitcoinUtil.toHexString(blockDeflater.toBytes(block)));
                return false;
            }
        }

        return true;
    }
}

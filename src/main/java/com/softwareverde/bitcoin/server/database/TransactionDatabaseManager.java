package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.BlockChainId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.util.List;

public class TransactionDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected void _storeTransactionInputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            transactionInputDatabaseManager.storeTransactionInput(transactionId, transactionInput);
        }
    }

    protected void _storeTransactionOutputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);

        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            transactionOutputDatabaseManager.storeTransactionOutput(transactionId, transactionOutput);
        }
    }

    protected TransactionId _getTransactionIdFromHash(final BlockChainId blockChainId, final Hash transactionHash) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query(
                "SELECT transactions.id FROM transactions"+
                " INNER JOIN blocks ON blocks.id = transactions.block_id"+
                " INNER JOIN block_chain_segments ON blocks.block_chain_segment_id = block_chain_segments.id"+
                " INNER JOIN block_chain_block_segments ON block_chain_block_segments.block_chain_segment_id = block_chain_segments.id"+
                " WHERE block_chain_block_segments.block_chain_id = ? AND transactions.hash = ?")
                .setParameter(blockChainId)
                .setParameter(BitcoinUtil.toHexString(transactionHash))
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    protected TransactionId _getTransactionIdFromHash(final BlockId blockId, final Hash transactionHash) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query(
                "SELECT id FROM transactions WHERE block_id = ? AND hash = ?")
                .setParameter(blockId)
                .setParameter(BitcoinUtil.toHexString(transactionHash))
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    protected void _updateTransaction(final TransactionId transactionId, final BlockId blockId, final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        _databaseConnection.executeSql(
            new Query("UPDATE transactions SET hash = ?, block_id = ?, version = ?, has_witness_data = ?, lock_time = ? WHERE id = ?")
                .setParameter(BitcoinUtil.toHexString(transaction.getHash()))
                .setParameter(blockId)
                .setParameter(transaction.getVersion())
                .setParameter((transaction.hasWitnessData() ? 1 : 0))
                .setParameter(lockTime.getValue())
                .setParameter(transactionId)
        );
    }

    protected TransactionId _insertTransaction(final BlockId blockId, final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        return TransactionId.wrap(_databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, block_id, version, has_witness_data, lock_time) VALUES (?, ?, ?, ?, ?)")
                .setParameter(BitcoinUtil.toHexString(transaction.getHash()))
                .setParameter(blockId)
                .setParameter(transaction.getVersion())
                .setParameter((transaction.hasWitnessData() ? 1 : 0))
                .setParameter(lockTime.getValue())
        ));
    }

    public TransactionDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public TransactionId storeTransaction(final BlockId blockId, final Transaction transaction) throws DatabaseException {
        final TransactionId transactionId;
        {
            final TransactionId existingTransactionId = _getTransactionIdFromHash(blockId, transaction.getHash());
            if (existingTransactionId != null) {
                _updateTransaction(existingTransactionId, blockId, transaction);
                transactionId = existingTransactionId;
            }
            else {
                final TransactionId newTransactionId = _insertTransaction(blockId, transaction);
                transactionId = newTransactionId;
            }
        }

        _storeTransactionInputs(transactionId, transaction);
        _storeTransactionOutputs(transactionId, transaction);
        return transactionId;
    }

    public TransactionId getTransactionIdFromHash(final BlockChainId blockChainId, final Hash transactionHash) throws DatabaseException {
        return _getTransactionIdFromHash(blockChainId, transactionHash);
    }

    public TransactionId getTransactionIdFromHash(final BlockId blockId, final Hash transactionHash) throws DatabaseException {
        return _getTransactionIdFromHash(blockId, transactionHash);
    }
}

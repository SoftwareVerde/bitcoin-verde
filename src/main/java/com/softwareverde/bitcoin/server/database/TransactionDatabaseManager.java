package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

import java.util.List;

public class TransactionDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected void _insertTransactionInputs(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            transactionInputDatabaseManager.insertTransactionInput(blockChainSegmentId, transactionId, transactionInput);
        }
    }

    protected void _insertTransactionOutputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);

        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            transactionOutputDatabaseManager.insertTransactionOutput(transactionId, transactionOutput);
        }
    }

    /**
     * Returns the transaction that matches the provided transactionHash, or null if one was not found.
     *  A matched transaction must belong to a block that is connected to the blockChainSegmentId.
     */
    protected TransactionId _getTransactionIdFromHash(final BlockChainSegmentId blockChainSegmentId, final Sha256Hash transactionHash) throws DatabaseException {
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(_databaseConnection);

        final Boolean blockChainSegmentExists = blockChainDatabaseManager.blockChainSegmentExists(blockChainSegmentId);
        if (! blockChainSegmentExists) { return null; }

        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_id FROM transactions WHERE hash = ?")
                .setParameter(HexUtil.toHexString(transactionHash.getBytes()))
        );
        if (rows.isEmpty()) { return null; }

        int i=0;
        for (final Row row : rows) {
            BlockId blockId = BlockId.wrap(row.getLong("block_id"));
            while (true) {
                final BlockChainSegment transactionBlockChainSegment = blockChainDatabaseManager.getBlockChainSegment(blockId);
                if (transactionBlockChainSegment == null) { break; }

                final Boolean transactionMatchesBlockChainSegment = ( blockChainSegmentId.equals(transactionBlockChainSegment.getId()) );
                if (transactionMatchesBlockChainSegment) {
                    if (i > 0) {
                        Logger.log(Thread.currentThread().getName() + " - " + "Traversed " + (i) + " BlockChainSegments looking for " + transactionHash + " on BlockSegmentId: " + blockChainSegmentId);
                    }
                    return TransactionId.wrap(row.getLong("id"));
                }

                final BlockId newBlockId = transactionBlockChainSegment.getTailBlockId();
                if (blockId.equals(newBlockId)) { break; }

                blockId = newBlockId;

                i += 1;
            }
        }

        // Logger.log(Thread.currentThread().getName() + " - " + "Traversed " + (i) + " BlockChainSegments, but unable to find " + transactionHash + " on BlockSegmentId: " + blockChainSegmentId);
        return null;
    }

    public static long _aCount = 0L;
    public static long _aDuration = 0L;

    public static long _bCount = 0L;
    public static long _bDuration = 0L;

    public static long _cCount = 0L;
    public static long _cDuration = 0L;

    /**
     * Returns the transaction that matches the provided transactionHash, or null if one was not found.
     *  A matched transaction must belong to a block that has not been assigned a blockChainSegmentId.
     */
    protected TransactionId _getUncommittedTransactionIdFromHash(final Sha256Hash transactionHash) throws DatabaseException {
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(_databaseConnection);

final Long aStart = System.nanoTime();
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_id FROM transactions WHERE hash = ?")
                .setParameter(HexUtil.toHexString(transactionHash.getBytes()))
        );
        if (rows.isEmpty()) { return null; }
final Long aEnd = System.nanoTime();
_aCount += 1;
_aDuration += (aEnd - aStart);

final Long bStart = System.nanoTime();
        for (final Row row : rows) {
            final BlockId blockId = BlockId.wrap(row.getLong("block_id"));
            final BlockChainSegmentId transactionBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);
            if (transactionBlockChainSegmentId == null) {
final Long bEnd = System.nanoTime();
_bCount += 1;
_bDuration += (bEnd - bStart);
                return TransactionId.wrap(row.getLong("id"));
            }
        }
final Long bEnd = System.nanoTime();
_bCount += 1;
_bDuration += (bEnd - bStart);

        return null;
    }

    protected TransactionId _getTransactionIdFromHash(final BlockId blockId, final Sha256Hash transactionHash) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query(
                "SELECT id FROM transactions WHERE block_id = ? AND hash = ?")
                .setParameter(blockId)
                .setParameter(HexUtil.toHexString(transactionHash.getBytes()))
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    protected void _updateTransaction(final TransactionId transactionId, final BlockId blockId, final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        _databaseConnection.executeSql(
            new Query("UPDATE transactions SET hash = ?, block_id = ?, version = ? = ?, lock_time = ? WHERE id = ?")
                .setParameter(HexUtil.toHexString(transaction.getHash().getBytes()))
                .setParameter(blockId)
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
                .setParameter(transactionId)
        );
    }

    protected TransactionId _insertTransaction(final BlockId blockId, final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        return TransactionId.wrap(_databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, block_id, version, lock_time) VALUES (?, ?, ?, ?)")
                .setParameter(transaction.getHash())
                .setParameter(blockId)
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
        ));
    }

    public TransactionDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public long _transactionInputCount = 0L;
    public long _transactionInputDuration = 0L;

    public long _transactionOutputCount = 0L;
    public long _transactionOutputDuration = 0L;

    public TransactionId insertTransaction(final BlockChainSegmentId blockChainSegmentId, final BlockId blockId, final Transaction transaction) throws DatabaseException {
        final TransactionId transactionId = _insertTransaction(blockId, transaction);

        long txInputStart = System.nanoTime();
        _insertTransactionInputs(blockChainSegmentId, transactionId, transaction);
        long txInputEnd = System.nanoTime();

        long txOutputStart = System.nanoTime();
        _insertTransactionOutputs(transactionId, transaction);
        long txOutputEnd = System.nanoTime();

        _transactionInputCount += transaction.getTransactionInputs().getSize();
        _transactionOutputCount += transaction.getTransactionOutputs().getSize();
        _transactionInputDuration += (txInputEnd - txInputStart);
        _transactionOutputDuration += (txOutputEnd - txOutputStart);

        return transactionId;
    }

    /**
     * Attempts to find the transaction that matches transactionHash that was published on the provided blockChainSegmentId.
     *  This function is intended to be used when the blockId is not known.
     *  Uncommitted transactions are NOT included in this search.
     */
    public TransactionId getTransactionIdFromHash(final BlockChainSegmentId blockChainSegmentId, final Sha256Hash transactionHash) throws DatabaseException {
        return _getTransactionIdFromHash(blockChainSegmentId, transactionHash);
    }

    /**
     * Attempts to find the transaction that matches transactionHash that has not been committed to a block.
     *  This function is intended to be used when the blockId is not known.
     *  Only uncommitted transactions (i.e. transactions have not been assigned a block or ones that associated to the block
     *  that is currently being stored...) are included in the search.
     */
    public TransactionId getUncommittedTransactionIdFromHash(final Sha256Hash transactionHash) throws DatabaseException {
        return _getUncommittedTransactionIdFromHash(transactionHash);
    }

    public TransactionId getTransactionIdFromHash(final BlockId blockId, final Sha256Hash transactionHash) throws DatabaseException {
        return _getTransactionIdFromHash(blockId, transactionHash);
    }

    public MutableTransaction fromDatabaseConnection(final TransactionId transactionId) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long version = row.getLong("version");
        final LockTime lockTime = new ImmutableLockTime(row.getLong("lock_time"));

        final MutableTransaction transaction = new MutableTransaction();

        transaction.setVersion(version);
        transaction.setLockTime(lockTime);

        final java.util.List<Row> transactionInputRows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_inputs WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        for (final Row transactionInputRow : transactionInputRows) {
            final TransactionInputId transactionInputId = TransactionInputId.wrap(transactionInputRow.getLong("id"));
            final TransactionInput transactionInput = transactionInputDatabaseManager.fromDatabaseConnection(transactionInputId);
            transaction.addTransactionInput(transactionInput);
        }

        final java.util.List<Row> transactionOutputRows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_outputs WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        for (final Row transactionOutputRow : transactionOutputRows) {
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionOutputRow.getLong("id"));
            final TransactionOutput transactionOutput = transactionOutputDatabaseManager.fromDatabaseConnection(transactionOutputId);
            transaction.addTransactionOutput(transactionOutput);
        }

        return transaction;
    }

    public BlockId getBlockId(final TransactionId transactionId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_id FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("block_id"));
    }
}

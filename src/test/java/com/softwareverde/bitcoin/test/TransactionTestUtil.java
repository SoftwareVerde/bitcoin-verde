package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;

public class TransactionTestUtil {

    protected static BlockId _getGenesisBlockId(final BlockchainSegmentId blockchainSegmentId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final DatabaseManagerCache databaseManagerCache = new DisabledDatabaseManagerCache();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, databaseManagerCache);
        final BlockId genesisBlockId = blockHeaderDatabaseManager.getBlockHeaderId(BlockHeader.GENESIS_BLOCK_HASH);
        if (genesisBlockId == null) {

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id FROM blocks WHERE block_height = 0 AND blockchain_segment_id = ?")
                    .setParameter(blockchainSegmentId)
            );
            if (! rows.isEmpty()) {
                final Long blockId = rows.get(0).getLong("id");
                return BlockId.wrap(blockId);
            }

            Logger.log("TEST: NOTE: Inserting genesis block.");

            final BlockInflater blockInflater = new BlockInflater();
            final String genesisBlockData = IoUtil.getResource("/blocks/" + HexUtil.toHexString(BlockHeader.GENESIS_BLOCK_HASH.getBytes()));
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(genesisBlockData));
            final BlockId blockId;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockId = blockDatabaseManager.insertBlock(block);
            }

            databaseConnection.executeSql(
                new Query("UPDATE blocks SET block_height = ?, blockchain_segment_id = ? WHERE id = ?")
                    .setParameter(Integer.MAX_VALUE)
                    .setParameter(blockchainSegmentId)
                    .setParameter(blockId)
            );

            return blockId;
        }

        return genesisBlockId;
    }

    public static List<Sha256Hash> getTransactionHashes(final List<Transaction> transactions) {
        final MutableList<Sha256Hash> transactionHashes = new MutableList<Sha256Hash>(transactions.getSize());
        for (final Transaction transaction : transactions) {
            transactionHashes.add(transaction.getHash());
        }
        return transactionHashes;
    }

    public static void createRequiredTransactionInputs(final BlockchainSegmentId blockchainSegmentId, final Transaction transaction, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        createRequiredTransactionInputs(blockchainSegmentId, transaction, databaseConnection, new MutableList<Sha256Hash>(0));
    }

    public static void createRequiredTransactionInputs(final BlockchainSegmentId blockchainSegmentId, final Transaction transaction, final MysqlDatabaseConnection databaseConnection, final List<Sha256Hash> excludedTransactionHashes) throws DatabaseException {
        final DatabaseManagerCache databaseManagerCache = new DisabledDatabaseManagerCache();
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(databaseConnection, databaseManagerCache);

        final BlockId genesisBlockId = _getGenesisBlockId(blockchainSegmentId, databaseConnection);

        // Ensure that all of the Transaction's TransactionInput's have outputs that exist...
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            if (excludedTransactionHashes.contains(previousOutputTransactionHash)) { continue; }
            if (Util.areEqual(previousOutputTransactionHash, new ImmutableSha256Hash())) { continue; }

            final TransactionOutputId transactionOutputId = transactionInputDatabaseManager.findPreviousTransactionOutputId(transactionInput);
            if (transactionOutputId != null) { continue; }

            final TransactionId transactionId;
            final java.util.List<Row> transactionRows = databaseConnection.query(new Query("SELECT id FROM transactions WHERE hash = ?").setParameter(previousOutputTransactionHash));
            if (transactionRows.isEmpty()) {
                Logger.log("TEST: NOTE: Mutating genesis block; adding fake transaction with hash: " + previousOutputTransactionHash);

                transactionId = TransactionId.wrap(databaseConnection.executeSql(
                    new Query("INSERT INTO transactions (hash, version, lock_time) VALUES (?, ?, ?)")
                        .setParameter(previousOutputTransactionHash)
                        .setParameter(Transaction.VERSION)
                        .setParameter(LockTime.MIN_TIMESTAMP.getValue())
                ));

                final Integer sortOrder;
                {
                    final java.util.List<Row> rows = databaseConnection.query(
                        new Query("SELECT COUNT(*) AS transaction_count FROM block_transactions WHERE block_id = ?")
                            .setParameter(genesisBlockId)
                    );
                    final Row row = rows.get(0);
                    sortOrder = row.getInteger("transaction_count");
                }

                databaseConnection.executeSql(
                    new Query("INSERT INTO block_transactions (block_id, transaction_id, sort_order) VALUES (?, ?, ?)")
                        .setParameter(genesisBlockId)
                        .setParameter(transactionId)
                        .setParameter(sortOrder)
                );
            }
            else {
                transactionId = TransactionId.wrap(transactionRows.get(0).getLong("id"));
            }

            final java.util.List<Row> transactionOutputRows = databaseConnection.query(new Query("SELECT id FROM transaction_outputs WHERE transaction_id = ? AND `index` = ?").setParameter(transactionId).setParameter(transactionInput.getPreviousOutputIndex()));
            if (transactionOutputRows.isEmpty()) {
                Logger.log("TEST: NOTE: Mutating transaction: " + previousOutputTransactionHash);

                final Long newTransactionOutputId = databaseConnection.executeSql(
                    new Query("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)")
                        .setParameter(transactionId)
                        .setParameter(transactionInput.getPreviousOutputIndex())
                        .setParameter(Long.MAX_VALUE)
                );

                databaseConnection.executeSql(
                    new Query("INSERT INTO locking_scripts (script_type_id, transaction_output_id, script, address_id) VALUES (?, ?, ?, ?)")
                        .setParameter(1L)
                        .setParameter(newTransactionOutputId)
                        .setParameter(new byte[0])
                        .setParameter(null)
                );
            }
        }
    }
}

package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;

public class TransactionTestUtil {

    protected static BlockId _getGenesisBlockId(final BlockChainSegmentId blockChainSegmentId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockId genesisBlockId = blockDatabaseManager.getBlockIdFromHash(BlockHeader.GENESIS_BLOCK_HASH);
        if (genesisBlockId == null) {

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id FROM blocks WHERE block_height = 0 AND block_chain_segment_id = ?")
                    .setParameter(blockChainSegmentId)
            );
            if (! rows.isEmpty()) {
                final Long blockId = rows.get(0).getLong("id");
                return BlockId.wrap(blockId);
            }

            Logger.log("TEST: NOTE: Inserting genesis block.");

            final BlockInflater blockInflater = new BlockInflater();
            final String genesisBlockData = IoUtil.getResource("/blocks/" + HexUtil.toHexString(BlockHeader.GENESIS_BLOCK_HASH.getBytes()));
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(genesisBlockData));
            final BlockId blockId = blockDatabaseManager.insertBlock(block);

            databaseConnection.executeSql(
                new Query("UPDATE blocks SET block_height = ?, block_chain_segment_id = ? WHERE id = ?")
                    .setParameter(Integer.MAX_VALUE)
                    .setParameter(blockChainSegmentId)
                    .setParameter(blockId)
            );

            return blockId;
        }

        return genesisBlockId;
    }

    public static void makeFakeTransactionInsertable(final BlockChainSegmentId blockChainSegmentId, final Transaction transaction, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(databaseConnection);

        // Ensure that all of the Transaction's TransactionInput's have outputs that exist...
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputId transactionOutputId = transactionInputDatabaseManager.findPreviousTransactionOutputId(blockChainSegmentId, transactionInput);
            if (transactionOutputId != null) { continue; }

            final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            if (Util.areEqual(previousOutputTransactionHash, new ImmutableSha256Hash())) { continue; }

            Logger.log("TEST: NOTE: Mutating genesis block; adding fake transaction with hash: " + previousOutputTransactionHash);

            final TransactionId transactionId = TransactionId.wrap(databaseConnection.executeSql(
                new Query("INSERT INTO transactions (hash, block_id, version, lock_time) VALUES (?, ?, ?, ?)")
                    .setParameter(previousOutputTransactionHash)
                    .setParameter(_getGenesisBlockId(blockChainSegmentId, databaseConnection))
                    .setParameter(Transaction.VERSION)
                    .setParameter(LockTime.MIN_TIMESTAMP.getValue())
            ));

            databaseConnection.executeSql(
                new Query("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)")
                    .setParameter(transactionId)
                    .setParameter(transactionInput.getPreviousOutputIndex())
                    .setParameter(Long.MAX_VALUE)
            );
        }
    }
}

package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

import java.math.BigInteger;

public class QueryBalanceHandler implements JsonRpcSocketServerHandler.QueryBalanceHandler {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public QueryBalanceHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    @Override
    public Long getBalance(final Address address) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            final BlockChainSegmentId headChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(headBlockId);

            final java.util.List<Row> rows = databaseConnection.query(
                new Query(
                    "SELECT " +
                        "blocks.id AS block_id, " +
                        "transaction_outputs.amount AS amount " +
                    "FROM " +
                        "addresses " +
                        "INNER JOIN locking_scripts " +
                            "ON locking_scripts.address_id = addresses.id " +
                        "INNER JOIN transaction_outputs " +
                            "ON transaction_outputs.id = locking_scripts.transaction_output_id " +
                        "INNER JOIN transactions " +
                            "ON transactions.id = transaction_outputs.transaction_id " +
                        "INNER JOIN blocks " +
                            "ON blocks.id = transactions.block_id " +
                    "WHERE " +
                        "addresses.address = ?"
                )
                .setParameter(address.toBase58CheckEncoded())
            );

            BigInteger totalAmount = BigInteger.ZERO;
            for (final Row row : rows) {
                final BlockId blockId = BlockId.wrap(row.getLong("block_id"));
                final Long amount = row.getLong("amount");

                final Boolean transactionIsOnMainChain = blockDatabaseManager.isBlockConnectedToChain(blockId, headChainSegmentId);
                if (transactionIsOnMainChain) {
                    totalAmount = totalAmount.add(BigInteger.valueOf(amount));
                }
            }

            return totalAmount.longValue();
        }
        catch (final Exception exception) {
            Logger.log(exception);
            return null;
        }
    }
}

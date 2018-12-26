package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.*;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

import java.math.BigInteger;

public class QueryAddressHandler implements JsonRpcSocketServerHandler.QueryAddressHandler {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected DatabaseManagerCache _databaseManagerCache;

    public QueryAddressHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    @Override
    public Long getBalance(final Address address) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockchainSegmentId headChainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

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

                final Boolean transactionIsOnMainChain = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, headChainSegmentId, BlockRelationship.ANY);
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

    @Override
    public List<Transaction> getAddressTransactions(final Address address) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
            final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(databaseConnection, _databaseManagerCache);

            final AddressId addressId = addressDatabaseManager.getAddressId(address.toBase58CheckEncoded());

            final List<TransactionId> transactionIds = addressDatabaseManager.getTransactionIds(addressId);
            final ImmutableListBuilder<Transaction> transactions = new ImmutableListBuilder<Transaction>(transactionIds.getSize());

            for (final TransactionId transactionId : transactionIds) {
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                transactions.add(transaction);
            }

            return transactions.build();
        }
        catch (final Exception exception) {
            Logger.log(exception);
            return null;
        }
    }
}

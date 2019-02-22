package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.SortUtil;

import java.util.HashMap;

public class QueryAddressHandler implements NodeRpcHandler.QueryAddressHandler {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected DatabaseManagerCache _databaseManagerCache;

    public QueryAddressHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    @Override
    public Long getBalance(final Address address) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockchainSegmentId headChainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(databaseConnection, _databaseManagerCache);

            final AddressId addressId = addressDatabaseManager.getAddressId(address.toBase58CheckEncoded());
            return addressDatabaseManager.getAddressBalance(headChainSegmentId, addressId);
        }
        catch (final Exception exception) {
            Logger.log(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getAddressTransactions(final Address address) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
            final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(databaseConnection, _databaseManagerCache);

            final AddressId addressId = addressDatabaseManager.getAddressId(address.toBase58CheckEncoded());
            final BlockchainSegmentId headChainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final List<TransactionId> transactionIds = addressDatabaseManager.getTransactionIds(headChainSegmentId, addressId, true);

            final MutableList<Transaction> pendingTransactions = new MutableList<Transaction>(0);
            final HashMap<Long, MutableList<Transaction>> transactionTimestamps = new HashMap<Long, MutableList<Transaction>>(transactionIds.getSize());

            for (final TransactionId transactionId : transactionIds) {
                final BlockId blockId = transactionDatabaseManager.getBlockId(headChainSegmentId, transactionId);
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);

                if (blockId != null) {
                    final Long transactionTimestamp = blockHeaderDatabaseManager.getBlockTimestamp(blockId);

                    MutableList<Transaction> transactions = transactionTimestamps.get(transactionTimestamp);
                    if (transactions == null) {
                        transactions = new MutableList<Transaction>(1);
                        transactionTimestamps.put(transactionTimestamp, transactions);
                    }

                    transactions.add(transaction);
                }
                else {
                    pendingTransactions.add(transaction);
                }
            }

            final ImmutableListBuilder<Transaction> transactions = new ImmutableListBuilder<Transaction>(transactionIds.getSize());
            { // Add the Transactions in descending order by timestamp...
                final MutableList<Long> timestamps = new MutableList<Long>(transactionTimestamps.keySet());
                timestamps.sort(SortUtil.longComparator.reversed());

                transactions.addAll(pendingTransactions); // Display unconfirmed transactions first...

                for (final Long timestamp : timestamps) {
                    transactions.addAll(transactionTimestamps.get(timestamp));
                }
            }

            return transactions.build();
        }
        catch (final Exception exception) {
            Logger.log(exception);
            return null;
        }
    }
}

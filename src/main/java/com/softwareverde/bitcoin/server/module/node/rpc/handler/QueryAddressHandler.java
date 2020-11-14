package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.SortUtil;

import java.util.HashMap;

public class QueryAddressHandler implements NodeRpcHandler.QueryAddressHandler {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public QueryAddressHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public Long getBalance(final Address address) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();

            final BlockchainSegmentId headChainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            return blockchainIndexerDatabaseManager.getAddressBalance(headChainSegmentId, address);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getAddressTransactions(final Address address) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();

            final BlockchainSegmentId headChainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final List<TransactionId> transactionIds = blockchainIndexerDatabaseManager.getTransactionIds(headChainSegmentId, address, true);

            final MutableList<Transaction> pendingTransactions = new MutableList<Transaction>(0);
            final HashMap<Long, MutableList<Transaction>> transactionTimestamps = new HashMap<Long, MutableList<Transaction>>(transactionIds.getCount());

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

                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                }
                else if (transaction != null) {
                    pendingTransactions.add(transaction);
                }
            }

            final ImmutableListBuilder<Transaction> transactions = new ImmutableListBuilder<Transaction>(transactionIds.getCount());
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
            Logger.warn(exception);
            return null;
        }
    }
}

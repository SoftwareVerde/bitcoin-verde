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
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.SortUtil;

import java.util.HashMap;

public class QueryAddressHandler implements NodeRpcHandler.QueryAddressHandler {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected Long _getBalance(final Address address, final Sha256Hash scriptHash, final Boolean includeUnconfirmedTransactions, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
        final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();

        final BlockchainSegmentId headChainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

        if (address != null) {
            return blockchainIndexerDatabaseManager.getAddressBalance(headChainSegmentId, address, includeUnconfirmedTransactions);
        }
        else {
            return blockchainIndexerDatabaseManager.getAddressBalance(headChainSegmentId, scriptHash, includeUnconfirmedTransactions);
        }
    }

    protected List<Transaction> _getAddressTransactions(final Address address, final Sha256Hash scriptHash, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();

        final BlockchainSegmentId headChainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

        final List<TransactionId> transactionIds;
        if (address != null) {
            transactionIds = blockchainIndexerDatabaseManager.getTransactionIds(headChainSegmentId, address, true);
        }
        else {
            transactionIds = blockchainIndexerDatabaseManager.getTransactionIds(headChainSegmentId, scriptHash, true);
        }

        final MutableList<Transaction> pendingTransactions = new MutableList<>(0);
        final HashMap<Long, MutableList<Transaction>> transactionTimestamps = new HashMap<>(transactionIds.getCount());

        for (final TransactionId transactionId : transactionIds) {
            final BlockId blockId = transactionDatabaseManager.getBlockId(headChainSegmentId, transactionId);
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);

            if (blockId != null) {
                final Long transactionTimestamp = blockHeaderDatabaseManager.getBlockTimestamp(blockId);

                MutableList<Transaction> transactions = transactionTimestamps.get(transactionTimestamp);
                if (transactions == null) {
                    transactions = new MutableList<>(1);
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

        final ImmutableListBuilder<Transaction> transactions = new ImmutableListBuilder<>(transactionIds.getCount());
        { // Add the Transactions in descending order by timestamp...
            final MutableList<Long> timestamps = new MutableList<>(transactionTimestamps.keySet());
            timestamps.sort(SortUtil.longComparator.reversed());

            transactions.addAll(pendingTransactions); // Display unconfirmed transactions first...

            for (final Long timestamp : timestamps) {
                transactions.addAll(transactionTimestamps.get(timestamp));
            }
        }

        return transactions.build();
    }

    public QueryAddressHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public Long getBalance(final Address address, final Boolean includeUnconfirmedTransactions) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            return _getBalance(address, null, includeUnconfirmedTransactions, databaseManager);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public Long getBalance(final Sha256Hash scriptHash, final Boolean includeUnconfirmedTransactions) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            return _getBalance(null, scriptHash, includeUnconfirmedTransactions, databaseManager);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getAddressTransactions(final Address address) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            return _getAddressTransactions(address, null, databaseManager);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public List<Transaction> getAddressTransactions(final Sha256Hash scriptHash) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            return _getAddressTransactions(null, scriptHash, databaseManager);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }
}

package com.softwareverde.bitcoin.server.module.node.handler.block;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.message.type.query.response.InventoryMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.SpvUnconfirmedTransactionsHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.HashSet;

public class RequestSpvBlocksHandler implements BitcoinNode.RequestSpvBlocksHandler {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final SpvUnconfirmedTransactionsHandler _spvUnconfirmedTransactionsHandler;

    public RequestSpvBlocksHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory, final SpvUnconfirmedTransactionsHandler spvUnconfirmedTransactionsHandler) {
        _databaseManagerFactory = databaseManagerFactory;
        _spvUnconfirmedTransactionsHandler = spvUnconfirmedTransactionsHandler;
    }

    @Override
    public void run(final BitcoinNode bitcoinNode, final List<Address> addresses) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final HashSet<TransactionId> transactionIds = new HashSet<TransactionId>();
            for (final Address address : addresses) {
                final List<TransactionId> matchedTransactionIds = blockchainIndexerDatabaseManager.getTransactionIds(headBlockchainSegmentId, address, false);
                for (final TransactionId transactionId : matchedTransactionIds) {
                    transactionIds.add(transactionId);
                }
            }
            if (transactionIds.isEmpty()) { return; }

            final HashSet<BlockId> blockIds = new HashSet<BlockId>(transactionIds.size());
            for (final TransactionId transactionId : transactionIds) {
                final BlockId blockId = transactionDatabaseManager.getBlockId(headBlockchainSegmentId, transactionId);
                if (blockId == null) { continue; }

                blockIds.add(blockId);
            }
            if (blockIds.isEmpty()) { return; }

            final InventoryMessage inventoryMessage = new InventoryMessage();
            for (final BlockId blockId : blockIds) {
                final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
                if (blockHash == null) { continue; }

                inventoryMessage.addInventoryItem(new InventoryItem(InventoryItemType.MERKLE_BLOCK, blockHash));
            }

            bitcoinNode.queueMessage(inventoryMessage);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        if (_spvUnconfirmedTransactionsHandler != null) {
            _spvUnconfirmedTransactionsHandler.broadcastUnconfirmedTransactions(bitcoinNode);
        }
    }
}

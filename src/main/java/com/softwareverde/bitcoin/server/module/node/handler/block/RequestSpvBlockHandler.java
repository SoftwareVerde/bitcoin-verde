package com.softwareverde.bitcoin.server.module.node.handler.block;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.message.type.query.response.InventoryMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.module.node.database.address.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.SpvUnconfirmedTransactionsHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.HashSet;

public class RequestSpvBlockHandler implements BitcoinNode.RequestSpvBlocksCallback {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final SpvUnconfirmedTransactionsHandler _spvUnconfirmedTransactionsHandler;

    public RequestSpvBlockHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory, final SpvUnconfirmedTransactionsHandler spvUnconfirmedTransactionsHandler) {
        _databaseManagerFactory = databaseManagerFactory;
        _spvUnconfirmedTransactionsHandler = spvUnconfirmedTransactionsHandler;
    }

    @Override
    public void run(final List<Address> addresses, final BitcoinNode bitcoinNode) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final AddressDatabaseManager addressDatabaseManager = databaseManager.getAddressDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final HashSet<TransactionId> transactionIds = new HashSet<TransactionId>();
            for (final Address address : addresses) {
                final AddressId addressId = addressDatabaseManager.getAddressId(address);
                if (addressId == null) { continue; }

                final List<TransactionId> matchedTransactionIds = addressDatabaseManager.getTransactionIds(headBlockchainSegmentId, addressId, false);
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

                inventoryMessage.addInventoryItem(new InventoryItem(InventoryItemType.SPV_BLOCK, blockHash));
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

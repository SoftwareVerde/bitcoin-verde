package com.softwareverde.bitcoin.server.module.node.handler.block;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.message.type.query.response.InventoryMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.module.node.database.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;

import java.util.HashSet;

public class RequestSpvBlockHandler implements BitcoinNode.RequestSpvBlocksCallback {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    public RequestSpvBlockHandler(final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    @Override
    public void run(final List<Address> addresses, final BitcoinNode bitcoinNode) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
            final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final HashSet<TransactionId> transactionIds = new HashSet<>();
            for (final Address address : addresses) {
                final AddressId addressId = addressDatabaseManager.getAddressId(address);
                if (addressId == null) { continue; }

                final List<TransactionId> matchedTransactionIds = addressDatabaseManager.getTransactionIds(headBlockchainSegmentId, addressId, false);
                for (final TransactionId transactionId : matchedTransactionIds) {
                    transactionIds.add(transactionId);
                }
            }
            if (transactionIds.isEmpty()) { return; }

            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);

            final HashSet<BlockId> blockIds = new HashSet<>(transactionIds.size());
            for (final TransactionId transactionId : transactionIds) {
                final BlockId blockId = transactionDatabaseManager.getBlockId(headBlockchainSegmentId, transactionId);
                if (blockId == null) { continue; }

                blockIds.add(blockId);
            }
            if (blockIds.isEmpty()) { return; }

            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

            final InventoryMessage inventoryMessage = new InventoryMessage();
            for (final BlockId blockId : blockIds) {
                final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
                if (blockHash == null) { continue; }

                inventoryMessage.addInventoryItem(new InventoryItem(InventoryItemType.SPV_BLOCK, blockHash));
            }

            bitcoinNode.queueMessage(inventoryMessage);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }
}

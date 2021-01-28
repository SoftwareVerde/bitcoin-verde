package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRequestDataHandler implements BitcoinNode.RequestDataHandler {
    protected final ConcurrentHashMap<Sha256Hash, Transaction> _availableTransactions = new ConcurrentHashMap<Sha256Hash, Transaction>();
    protected final ConcurrentHashMap<Sha256Hash, Block> _availableBlocks = new ConcurrentHashMap<Sha256Hash, Block>();

    @Override
    public void run(final BitcoinNode bitcoinNode, final List<InventoryItem> dataHashes) {
        final MutableList<InventoryItem> notFoundDataHashes = new MutableList<InventoryItem>();

        for (final InventoryItem inventoryItem : dataHashes) {
            final Sha256Hash objectHash = inventoryItem.getItemHash();
            final InventoryItemType itemType = inventoryItem.getItemType();

            switch (itemType) {
                case BLOCK: {
                    final Block block = _availableBlocks.get(objectHash);
                    if (block == null) {
                        notFoundDataHashes.add(inventoryItem);
                        continue;
                    }

                    bitcoinNode.transmitBlock(block);
                } break;
                case TRANSACTION: {
                    final Transaction transaction = _availableTransactions.get(objectHash);
                    if (transaction == null) {
                        notFoundDataHashes.add(inventoryItem);
                        continue;
                    }

                    bitcoinNode.transmitTransaction(transaction);
                } break;
                default: {
                    notFoundDataHashes.add(inventoryItem);
                }
            }
        }

        if (! notFoundDataHashes.isEmpty()) {
            final NotFoundResponseMessage notFoundResponseMessage = new NotFoundResponseMessage();
            for (final InventoryItem inventoryItem : notFoundDataHashes) {
                notFoundResponseMessage.addItem(inventoryItem);
            }
            bitcoinNode.queueMessage(notFoundResponseMessage);
        }
    }
}

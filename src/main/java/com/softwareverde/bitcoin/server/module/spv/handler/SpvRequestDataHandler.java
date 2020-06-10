package com.softwareverde.bitcoin.server.module.spv.handler;

import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.concurrent.ConcurrentHashMap;

public class SpvRequestDataHandler implements BitcoinNode.RequestDataCallback {
    protected ConcurrentHashMap<Sha256Hash, Transaction> _spvTransactions = new ConcurrentHashMap<Sha256Hash, Transaction>();

    @Override
    public void run(final List<InventoryItem> inventoryItems, final BitcoinNode bitcoinNode) {
        for (final InventoryItem inventoryItem : inventoryItems) {
            switch (inventoryItem.getItemType()) {
                case TRANSACTION: {
                    final Sha256Hash transactionHash = inventoryItem.getItemHash();
                    final Transaction transaction = _spvTransactions.get(transactionHash);
                    if (transaction == null) { continue; }

                    bitcoinNode.transmitTransaction(transaction);
                } break;
            }
        }
    }

    public void addSpvTransaction(final Transaction transaction) {
        _spvTransactions.put(transaction.getHash(), transaction.asConst());
    }
}

package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;

import java.util.WeakHashMap;

public class RequestDataHandlerMonitor implements BitcoinNode.RequestDataCallback {
    public static final int BAN_THRESHOLD = 1024;
    public static final float MAX_FALSE_POSITIVE_RATE = 0.15F;

    protected static final Object MUTEX = new Object();
    protected static final MutableBloomFilter PREVIOUS_TRANSACTIONS = MutableBloomFilter.wrap(new MutableByteArray(131072), 3, 0L); // 0.10 false positive rate at 218k items...
    protected static final WeakHashMap<BitcoinNode, Container<Integer>> NODE_SCORES = new WeakHashMap<BitcoinNode, Container<Integer>>(128);
    protected static long FILTER_TRANSACTION_COUNT = 0L;

    public static RequestDataHandlerMonitor wrap(final BitcoinNode.RequestDataCallback core) {
        if (core instanceof RequestDataHandlerMonitor) {
            Logger.log("NOTICE: Attempted to wrap RequestDataHandlerMonitor.");
            return new RequestDataHandlerMonitor(((RequestDataHandlerMonitor) core)._core);
        }

        return new RequestDataHandlerMonitor(core);
    }

    protected final BitcoinNode.RequestDataCallback _core;

    protected void _checkFalsePositives() {
        final Float falsePositiveRate = PREVIOUS_TRANSACTIONS.getFalsePositiveRate(FILTER_TRANSACTION_COUNT);
        if (falsePositiveRate >= MAX_FALSE_POSITIVE_RATE) {
            Logger.log("Resetting BanningRequestDataHandler BloomFilter. Item Count: " + FILTER_TRANSACTION_COUNT);
            PREVIOUS_TRANSACTIONS.clear();
            FILTER_TRANSACTION_COUNT = 0L;
        }
    }

    protected RequestDataHandlerMonitor(final BitcoinNode.RequestDataCallback core) {
        _core = core;
    }

    @Override
    public void run(final List<InventoryItem> dataHashes, final BitcoinNode bitcoinNode) {
        for (final InventoryItem inventoryItem : dataHashes) {
            if (inventoryItem.getItemType() == InventoryItemType.TRANSACTION) {
                final Sha256Hash transactionHash = inventoryItem.getItemHash();
                synchronized (MUTEX) {
                    final Boolean transactionWasSeenBefore = PREVIOUS_TRANSACTIONS.containsItem(transactionHash);
                    if (! NODE_SCORES.containsKey(bitcoinNode)) {
                        NODE_SCORES.put(bitcoinNode, new Container<Integer>(0));
                    }

                    final Container<Integer> nodeScore = NODE_SCORES.get(bitcoinNode);
                    nodeScore.value += ((transactionWasSeenBefore ? 1 : -1));
                    if (nodeScore.value < BAN_THRESHOLD) {
                        Logger.log("Disconnecting BitcoinNode " + bitcoinNode.getIp() + bitcoinNode.getUserAgent() + " - Requesting too many unusual Transactions. Score: " + nodeScore.value);
                        bitcoinNode.disconnect(); // TODO: Consider banning node...
                        return;
                    }

                    _checkFalsePositives();

                    if (! transactionWasSeenBefore) {
                        PREVIOUS_TRANSACTIONS.addItem(transactionHash);
                        FILTER_TRANSACTION_COUNT += 1L;
                    }
                }
            }

            _core.run(dataHashes, bitcoinNode);
        }
    }

    /**
     * TransactionHashes added via this method will not penalize nodes for requesting them.
     *  NOTE: Transactions added via this method will no longer be safe after the filter has become full.
     */
    public void addTransactionHash(final Sha256Hash transactionHash) {
        synchronized (MUTEX) {
            _checkFalsePositives();

            final Boolean transactionWasSeenBefore = PREVIOUS_TRANSACTIONS.containsItem(transactionHash);
            if (! transactionWasSeenBefore) {
                PREVIOUS_TRANSACTIONS.addItem(transactionHash);
                FILTER_TRANSACTION_COUNT += 1L;
            }
        }
    }
}
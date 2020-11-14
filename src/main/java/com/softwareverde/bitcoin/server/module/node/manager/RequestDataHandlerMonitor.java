package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;

import java.util.WeakHashMap;

public class RequestDataHandlerMonitor implements BitcoinNode.RequestDataHandler, TransactionWhitelist {
    public static final int BAN_THRESHOLD = -128;
    public static final float MAX_FALSE_POSITIVE_RATE = 0.15F;

    protected static final Object MUTEX = new Object();
    protected static final MutableBloomFilter PREVIOUS_TRANSACTIONS = MutableBloomFilter.wrap(new MutableByteArray(131072), 3, 0L); // 0.10 false positive rate at 218k items...
    protected static final WeakHashMap<BitcoinNode, Container<Integer>> NODE_SCORES = new WeakHashMap<BitcoinNode, Container<Integer>>(128);
    protected static long FILTER_TRANSACTION_COUNT = 0L;

    public static RequestDataHandlerMonitor wrap(final BitcoinNode.RequestDataHandler core) {
        if (core instanceof RequestDataHandlerMonitor) {
            Logger.warn("Attempted to wrap RequestDataHandlerMonitor.");
            return new RequestDataHandlerMonitor(((RequestDataHandlerMonitor) core)._core);
        }

        return new RequestDataHandlerMonitor(core);
    }

    protected final BitcoinNode.RequestDataHandler _core;

    protected void _checkFalsePositives() {
        final Float falsePositiveRate = PREVIOUS_TRANSACTIONS.getFalsePositiveRate(FILTER_TRANSACTION_COUNT);
        if (falsePositiveRate >= MAX_FALSE_POSITIVE_RATE) {
            Logger.debug("Resetting BanningRequestDataHandler BloomFilter. Item Count: " + FILTER_TRANSACTION_COUNT);
            PREVIOUS_TRANSACTIONS.clear();
            FILTER_TRANSACTION_COUNT = 0L;
        }
    }

    protected RequestDataHandlerMonitor(final BitcoinNode.RequestDataHandler core) {
        _core = core;
    }

    @Override
    public void run(final BitcoinNode bitcoinNode, final List<InventoryItem> dataHashes) {
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
                        Logger.debug("Disconnecting BitcoinNode " + bitcoinNode.getIp() + bitcoinNode.getUserAgent() + " - Requesting too many unusual Transactions. Score: " + nodeScore.value);
                        bitcoinNode.disconnect();
                        return;
                    }

                    _checkFalsePositives();

                    // NOTE: Adding other requested transactions is disabled because attackers often request the same unusual transactions.
                    //  Instead, the Monitor is filled to represent the state of the MemPool via RequestDataHandlerMonitor::addTransactionHash.
                    // if (! transactionWasSeenBefore) {
                    //     PREVIOUS_TRANSACTIONS.addItem(transactionHash);
                    //     FILTER_TRANSACTION_COUNT += 1L;
                    // }
                }
            }
        }

        _core.run(bitcoinNode, dataHashes);
    }

    /**
     * TransactionHashes added via this method will not penalize nodes for requesting them.
     *  NOTE: Transactions added via this method will no longer be safe after the filter has become full.
     */
    @Override
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
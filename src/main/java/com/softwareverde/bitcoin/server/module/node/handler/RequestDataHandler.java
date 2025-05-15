package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.bitcoin.server.module.node.TransactionMempool;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof.DoubleSpendProofStore;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class RequestDataHandler implements BitcoinNode.RequestDataHandler {
    public static final BitcoinNode.RequestDataHandler IGNORE_REQUESTS_HANDLER = new BitcoinNode.RequestDataHandler() {
        @Override
        public void run(final BitcoinNode bitcoinNode, final List<InventoryItem> dataHashes) { }
    };

    protected final AtomicBoolean _isShuttingDown = new AtomicBoolean(false);

    protected final Blockchain _blockchain;
    protected final BlockStore _blockStore;
    protected final TransactionMempool _transactionMempool;
    protected final DoubleSpendProofStore _doubleSpendProofStore;

    public RequestDataHandler(final Blockchain blockchain, final BlockStore blockStore, final TransactionMempool transactionMempool, final DoubleSpendProofStore doubleSpendProofStore) {
        _blockchain = blockchain;
        _blockStore = blockStore;
        _transactionMempool = transactionMempool;
        _doubleSpendProofStore = doubleSpendProofStore;
    }

    @Override
    public void run(final BitcoinNode bitcoinNode, final List<InventoryItem> dataHashes) {
        if (_isShuttingDown.get()) { return; }

        final MutableList<InventoryItem> notFoundInventory = new MutableArrayList<>();

        final HashSet<InventoryItem> processedDataHashes = new HashSet<>(dataHashes.getCount());

        for (final InventoryItem inventoryItem : dataHashes) {
            { // Avoid duplicate inventoryItems... This was encountered during the initial block download of an Android SPV wallet.
                if (processedDataHashes.contains(inventoryItem)) { continue; }
                processedDataHashes.add(inventoryItem);
            }

            if (! bitcoinNode.isConnected()) { break; }

            final InventoryItemType inventoryItemType = inventoryItem.getItemType();
            switch (inventoryItemType) {

                case MERKLE_BLOCK:
                case BLOCK: {
                    final NanoTimer getBlockDataTimer = new NanoTimer();
                    getBlockDataTimer.start();
                    final Sha256Hash blockHash = inventoryItem.getItemHash();

                    final Long blockHeight = _blockchain.getBlockHeight(blockHash);
                    if (blockHeight == null) {
                        notFoundInventory.add(inventoryItem);
                        continue;
                    }

                    final Block block = _blockStore.getBlock(blockHash, blockHeight);

                    if (block == null) {
                        Logger.debug(bitcoinNode + " requested unknown block: " + blockHash);
                        notFoundInventory.add(inventoryItem);
                        continue;
                    }

                    if (inventoryItem.getItemType() == InventoryItemType.MERKLE_BLOCK) {
                        bitcoinNode.transmitMerkleBlock(block);
                    }
                    else {
                        bitcoinNode.transmitBlock(block);
                    }

                    getBlockDataTimer.stop();
                    Logger.debug("GetBlockData: " + blockHash + " "  + bitcoinNode.getRemoteNodeIpAddress() + " " + getBlockDataTimer.getMillisecondsElapsed() + "ms");

                    final Sha256Hash batchContinueHash = bitcoinNode.getBatchContinueHash();
                    if (Util.areEqual(batchContinueHash, blockHash)) {
                        final Sha256Hash headBlockHash = _blockchain.getHeadBlockHeaderHash();
                        bitcoinNode.transmitBatchContinueHash(headBlockHash);
                    }
                } break;

                case TRANSACTION: {
                    final NanoTimer getTransactionTimer = new NanoTimer();
                    getTransactionTimer.start();

                    final Sha256Hash transactionHash = inventoryItem.getItemHash();
                    final TransactionWithFee transaction = _transactionMempool.getTransaction(transactionHash);
                    if (transaction == null) {
                        notFoundInventory.add(inventoryItem);
                        continue;
                    }

                    bitcoinNode.transmitTransaction(transaction.transaction);

                    getTransactionTimer.stop();
                    Logger.info("GetTransactionData: " + transactionHash + " to " + bitcoinNode.getRemoteNodeIpAddress() + " " + getTransactionTimer.getMillisecondsElapsed() + "ms.");
                } break;

                case DOUBLE_SPEND_PROOF: {
                    if (_doubleSpendProofStore == null) {
                        Logger.debug("No DoubleSpendProof store available.");
                        continue;
                    }

                    final Sha256Hash doubleSpendProofHash = inventoryItem.getItemHash();
                    final DoubleSpendProof doubleSpendProof = _doubleSpendProofStore.getDoubleSpendProof(doubleSpendProofHash);

                    if (doubleSpendProof == null) {
                        Logger.debug(bitcoinNode + " requested unknown DoubleSpendProof: " + doubleSpendProofHash);
                        notFoundInventory.add(inventoryItem);
                        continue;
                    }

                    bitcoinNode.transmitDoubleSpendProof(doubleSpendProof);
                    Logger.info("GetTransactionData: DSProof " + doubleSpendProofHash + " to " + bitcoinNode.getRemoteNodeIpAddress() + ".");
                } break;

                case UTXO_COMMITMENT_EVEN:
                case UTXO_COMMITMENT_ODD: {
                    // TODO: Reimplement Bitcoin Verde 3.0
                } break;

                default: {
                    Logger.debug("Unsupported RequestDataMessage Type: " + inventoryItem.getItemType());
                } break;
            }
        }

        if (! notFoundInventory.isEmpty()) {
            final NotFoundResponseMessage notFoundResponseMessage = new NotFoundResponseMessage();
            for (final InventoryItem inventoryItem : notFoundInventory) {
                notFoundResponseMessage.addItem(inventoryItem);
            }
            bitcoinNode.queueMessage(notFoundResponseMessage);
        }
    }

    public void shutdown() {
        _isShuttingDown.set(true);
    }
}

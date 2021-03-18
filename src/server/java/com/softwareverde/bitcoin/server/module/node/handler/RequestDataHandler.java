package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof.DoubleSpendProofStore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
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
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final DoubleSpendProofStore _doubleSpendProofStore;

    public RequestDataHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory, final DoubleSpendProofStore doubleSpendProofStore) {
        _databaseManagerFactory = databaseManagerFactory;
        _doubleSpendProofStore = doubleSpendProofStore;
    }

    @Override
    public void run(final BitcoinNode bitcoinNode, final List<InventoryItem> dataHashes) {
        if (_isShuttingDown.get()) { return; }

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final MutableList<InventoryItem> notFoundDataHashes = new MutableList<InventoryItem>();

            final HashSet<InventoryItem> processedDataHashes = new HashSet<InventoryItem>(dataHashes.getCount());

            for (final InventoryItem inventoryItem : dataHashes) {
                { // Avoid duplicate inventoryItems... This was encountered during the initial block download of an Android SPV wallet.
                    if (processedDataHashes.contains(inventoryItem)) { continue; }
                    processedDataHashes.add(inventoryItem);
                }

                if (! bitcoinNode.isConnected()) { break; }

                switch (inventoryItem.getItemType()) {

                    case MERKLE_BLOCK:
                    case BLOCK: {
                        final NanoTimer getBlockDataTimer = new NanoTimer();
                        getBlockDataTimer.start();
                        final Sha256Hash blockHash = inventoryItem.getItemHash();
                        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

                        if (blockId == null) {
                            notFoundDataHashes.add(inventoryItem);
                            continue;
                        }

                        final Block block = blockDatabaseManager.getBlock(blockId);

                        if (block == null) {
                            Logger.debug(bitcoinNode.getConnectionString() + " requested unknown block: " + blockHash);
                            notFoundDataHashes.add(inventoryItem);
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
                            final Sha256Hash headBlockHash = blockHeaderDatabaseManager.getHeadBlockHeaderHash();
                            bitcoinNode.transmitBatchContinueHash(headBlockHash);
                        }
                    } break;

                    case TRANSACTION: {
                        final NanoTimer getTransactionTimer = new NanoTimer();
                        getTransactionTimer.start();

                        final Sha256Hash transactionHash = inventoryItem.getItemHash();
                        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                        if (transactionId == null) {
                            notFoundDataHashes.add(inventoryItem);
                            continue;
                        }

                        final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                        if (transaction == null) {
                            Logger.debug(bitcoinNode.getConnectionString() + " requested unknown Transaction: " + transactionHash);
                            notFoundDataHashes.add(inventoryItem);
                            continue;
                        }

                        bitcoinNode.transmitTransaction(transaction);

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
                            Logger.debug(bitcoinNode.getConnectionString() + " requested unknown DoubleSpendProof: " + doubleSpendProofHash);
                            notFoundDataHashes.add(inventoryItem);
                            continue;
                        }

                        bitcoinNode.transmitDoubleSpendProof(doubleSpendProof);
                        Logger.info("GetTransactionData: DSProof " + doubleSpendProofHash + " to " + bitcoinNode.getRemoteNodeIpAddress() + ".");
                    } break;

                    default: {
                        Logger.debug("Unsupported RequestDataMessage Type: " + inventoryItem.getItemType());
                    } break;
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
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    public void shutdown() {
        _isShuttingDown.set(true);
    }
}

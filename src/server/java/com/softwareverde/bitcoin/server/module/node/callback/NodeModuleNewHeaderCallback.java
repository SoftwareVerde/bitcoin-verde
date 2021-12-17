package com.softwareverde.bitcoin.server.module.node.callback;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.module.node.UtxoCommitmentIndexer;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputVisitor;
import com.softwareverde.bitcoin.server.module.node.handler.BlockInventoryMessageHandler;
import com.softwareverde.bitcoin.server.module.node.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainIndexer;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.UtxoCommitmentDownloader;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

class NodeModuleNewHeaderCallback implements BlockHeaderDownloader.NewBlockHeadersAvailableCallback {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BlockHeaderDownloader _blockHeaderDownloader;
    protected final BlockDownloader _blockDownloader;
    protected final SynchronizationStatusHandler _synchronizationStatusHandler;
    protected final BlockInventoryMessageHandler _blockInventoryMessageHandler;

    public NodeModuleNewHeaderCallback(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BlockHeaderDownloader blockHeaderDownloader, final BlockDownloader blockDownloader, final SynchronizationStatusHandler synchronizationStatusHandler, final BlockInventoryMessageHandler blockInventoryMessageHandler) {
        _databaseManagerFactory = databaseManagerFactory;
        _blockHeaderDownloader = blockHeaderDownloader;
        _blockDownloader = blockDownloader;
        _synchronizationStatusHandler = synchronizationStatusHandler;
        _blockInventoryMessageHandler = blockInventoryMessageHandler;
    }

    @Override
    public synchronized void onNewHeadersReceived(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
        if (bitcoinNode != null) {
            final MutableList<Sha256Hash> blockHashes = new MutableList<>(blockHeaders.getCount());
            for (final BlockHeader blockHeader : blockHeaders) {
                final Sha256Hash blockHash = blockHeader.getHash();
                blockHashes.add(blockHash);
            }
            _blockInventoryMessageHandler.onNewInventory(bitcoinNode, blockHashes);
        }

        _blockDownloader.wakeUp();

        // TODO: If the BlockHeader(s) are on the main chain and the node is (mostly) synced, then prioritize the download
        //  of the new block via BlockDownloader::requestBlock(blockHash, 0L, bitcoinNode)

        final Long blockHeaderDownloaderBlockHeight = Util.coalesce(_blockHeaderDownloader.getBlockHeight());

        if (_synchronizationStatusHandler.getState() == State.ONLINE) {
            try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);

                if (blockHeaderDownloaderBlockHeight > Util.coalesce(headBlockHeight, -1L)) {
                    _synchronizationStatusHandler.setState(State.SYNCHRONIZING);
                }
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
            }
        }
    }
}

class NodeModuleNewHeaderCallbackWithFastSync extends NodeModuleNewHeaderCallback {
    protected final Long _maxCommitmentBlockHeight;
    protected final Boolean _indexModeIsEnabled;
    protected final UtxoCommitmentDownloader _utxoCommitmentDownloader;
    protected final BlockchainIndexer _blockchainIndexer;
    protected Boolean _fastSyncHasCompleted;
    protected final Runnable _onFastSyncComplete;

    public NodeModuleNewHeaderCallbackWithFastSync(
        final FullNodeDatabaseManagerFactory databaseManagerFactory, final BlockHeaderDownloader blockHeaderDownloader, final BlockDownloader blockDownloader, final SynchronizationStatusHandler synchronizationStatusHandler, final BlockInventoryMessageHandler blockInventoryMessageHandler,
        final Long maxCommitmentBlockHeight, final Boolean indexModeIsEnabled, final UtxoCommitmentDownloader utxoCommitmentDownloader, final BlockchainIndexer blockchainIndexer, final Runnable onFastSyncComplete
    ) {
        super(databaseManagerFactory, blockHeaderDownloader, blockDownloader, synchronizationStatusHandler, blockInventoryMessageHandler);
        _maxCommitmentBlockHeight = maxCommitmentBlockHeight;
        _indexModeIsEnabled = indexModeIsEnabled;
        _utxoCommitmentDownloader = utxoCommitmentDownloader;
        _blockchainIndexer = blockchainIndexer;
        _fastSyncHasCompleted = false;
        _onFastSyncComplete = onFastSyncComplete;
    }

    @Override
    public synchronized void onNewHeadersReceived(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
        super.onNewHeadersReceived(bitcoinNode, blockHeaders);

        if (_fastSyncHasCompleted) { return; }

        final Long blockHeaderDownloaderBlockHeight = Util.coalesce(_blockHeaderDownloader.getBlockHeight());
        final boolean headerSyncIsPastUtxoCommitmentHeight = (blockHeaderDownloaderBlockHeight >= _maxCommitmentBlockHeight);
        if (! headerSyncIsPastUtxoCommitmentHeight) { return; }

        Logger.debug("Pausing BlockHeaderDownloader for UTXO import...");
        _blockHeaderDownloader.pause();
        try {
            final MutableList<TransactionOutputIdentifier> transactionOutputIdentifierIndexBatch = new MutableList<>();
            final MutableList<TransactionOutput> transactionOutputIndexBatch = new MutableList<>();

            final UtxoCommitmentIndexer utxoCommitmentIndexer = new UtxoCommitmentIndexer(_blockchainIndexer, _databaseManagerFactory);
            final UnspentTransactionOutputVisitor unspentTransactionOutputVisitor;
            if (_indexModeIsEnabled) {
                unspentTransactionOutputVisitor = new UnspentTransactionOutputVisitor() {
                    @Override
                    public void run(final TransactionOutputIdentifier transactionOutputIdentifier, final UnspentTransactionOutput transactionOutput) throws Exception {
                        transactionOutputIdentifierIndexBatch.add(transactionOutputIdentifier);
                        transactionOutputIndexBatch.add(transactionOutput);

                        if (transactionOutputIdentifierIndexBatch.getCount() >= 1024) {
                            utxoCommitmentIndexer.indexFastSyncUtxos(transactionOutputIdentifierIndexBatch, transactionOutputIndexBatch);
                            transactionOutputIdentifierIndexBatch.clear();
                            transactionOutputIndexBatch.clear();
                        }
                    }
                };
            }
            else {
                unspentTransactionOutputVisitor = null;
            }

            final Boolean didComplete = _utxoCommitmentDownloader.runOnceSynchronously(unspentTransactionOutputVisitor);
            _fastSyncHasCompleted = didComplete;

            if (didComplete && _indexModeIsEnabled) {
                try {
                    if (! transactionOutputIdentifierIndexBatch.isEmpty()) {
                        utxoCommitmentIndexer.indexFastSyncUtxos(transactionOutputIdentifierIndexBatch, transactionOutputIndexBatch);
                        transactionOutputIdentifierIndexBatch.clear();
                        transactionOutputIndexBatch.clear();
                    }
                }
                catch (final DatabaseException exception) {
                    Logger.debug(exception);
                }

                if (_onFastSyncComplete != null) {
                    _onFastSyncComplete.run();
                }
            }
        }
        finally {
            // Blocks that are requested during the UTXO import should be cleared since they are usually early blocks...
            _blockDownloader.clearQueue();
            _blockHeaderDownloader.resume();
        }
    }
}
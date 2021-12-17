package com.softwareverde.bitcoin.server.module.node.callback;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.handler.BlockInventoryMessageHandler;
import com.softwareverde.bitcoin.server.module.node.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainIndexer;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.UtxoCommitmentDownloader;

public class NewBlockHeadersAvailableCallbackBuilder {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BlockHeaderDownloader _blockHeaderDownloader;
    protected final BlockDownloader _blockDownloader;
    protected final SynchronizationStatusHandler _synchronizationStatusHandler;
    protected final BlockInventoryMessageHandler _blockInventoryMessageHandler;

    protected Boolean _fastSyncIsEnabled;

    // Required properties for FastSync...
    protected Boolean _indexModeIsEnabled;
    protected Long _maxCommitmentBlockHeight;
    protected UtxoCommitmentDownloader _utxoCommitmentDownloader;
    protected BlockchainIndexer _blockchainIndexer;
    protected Runnable _onFastSyncComplete;

    public NewBlockHeadersAvailableCallbackBuilder(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BlockHeaderDownloader blockHeaderDownloader, final BlockDownloader blockDownloader, final SynchronizationStatusHandler synchronizationStatusHandler, final BlockInventoryMessageHandler blockInventoryMessageHandler) {
        _databaseManagerFactory = databaseManagerFactory;
        _blockHeaderDownloader = blockHeaderDownloader;
        _blockDownloader = blockDownloader;
        _synchronizationStatusHandler = synchronizationStatusHandler;
        _blockInventoryMessageHandler = blockInventoryMessageHandler;

        _fastSyncIsEnabled = false;
    }

    public void enableFastSync(final Long maxCommitmentBlockHeight, final Boolean indexModeIsEnabled, final UtxoCommitmentDownloader utxoCommitmentDownloader, final BlockchainIndexer blockchainIndexer, final Runnable onFastSyncComplete) {
        _maxCommitmentBlockHeight = maxCommitmentBlockHeight;
        _fastSyncIsEnabled = true;
        _indexModeIsEnabled = indexModeIsEnabled;
        _utxoCommitmentDownloader = utxoCommitmentDownloader;
        _blockchainIndexer = blockchainIndexer;
        _onFastSyncComplete = onFastSyncComplete;
    }

    public BlockHeaderDownloader.NewBlockHeadersAvailableCallback build() {
        if (_fastSyncIsEnabled) {
            return new NodeModuleNewHeaderCallbackWithFastSync(
                _databaseManagerFactory, _blockHeaderDownloader, _blockDownloader, _synchronizationStatusHandler, _blockInventoryMessageHandler,
                _maxCommitmentBlockHeight, _indexModeIsEnabled, _utxoCommitmentDownloader, _blockchainIndexer, _onFastSyncComplete
            );
        }

        return new NodeModuleNewHeaderCallback(_databaseManagerFactory, _blockHeaderDownloader, _blockDownloader, _synchronizationStatusHandler, _blockInventoryMessageHandler);
    }
}

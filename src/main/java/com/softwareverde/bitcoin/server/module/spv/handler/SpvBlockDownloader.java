package com.softwareverde.bitcoin.server.module.spv.handler;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sequentially downloads merkleBlocks received via SpvBlockInventoryMessageCallback::onResult.
 *  Failed blocks are not retried.
 */
public class SpvBlockDownloader implements BitcoinNode.SpvBlockInventoryMessageCallback {
    public interface MerkleBlockDownloader {
        void requestMerkleBlock(Sha256Hash blockHash, BitcoinNodeManager.DownloadMerkleBlockCallback callback);
    }

    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final MerkleBlockDownloader _merkleBlockDownloader;
    protected final ConcurrentLinkedQueue<Sha256Hash> _queuedBlockHashes = new ConcurrentLinkedQueue<Sha256Hash>();
    protected final AtomicBoolean _blockIsInFlight = new AtomicBoolean(false);

    protected final BitcoinNodeManager.DownloadMerkleBlockCallback _onMerkleBlockDownloaded = new BitcoinNodeManager.DownloadMerkleBlockCallback() {
        @Override
        public void onResult(final BitcoinNode.MerkleBlockParameters merkleBlockParameters) {
            _downloadNextMerkleBlock();
        }

        @Override
        public void onFailure(final Sha256Hash blockHash) {
            _downloadNextMerkleBlock();
        }
    };

    protected synchronized void _downloadNextMerkleBlock() {
        if (! _blockIsInFlight.compareAndSet(false, true)) { return; }

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            while (true) {
                final Sha256Hash blockHash = _queuedBlockHashes.poll();
                if (blockHash == null) { break; }

                if (! blockDatabaseManager.hasTransactions(blockHash)) {
                    _merkleBlockDownloader.requestMerkleBlock(blockHash, _onMerkleBlockDownloaded);
                    return;
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        // No block ended up being requested...
        _blockIsInFlight.set(false);
    }

    public SpvBlockDownloader(final DatabaseManagerFactory databaseManagerFactory, final MerkleBlockDownloader merkleBlockDownloader) {
        _databaseManagerFactory = databaseManagerFactory;
        _merkleBlockDownloader = merkleBlockDownloader;
    }

    @Override
    public void onResult(final List<Sha256Hash> blockHashes) {
        for (final Sha256Hash blockHash : blockHashes) {
            _queuedBlockHashes.add(blockHash);
        }

        _downloadNextMerkleBlock();
    }
}

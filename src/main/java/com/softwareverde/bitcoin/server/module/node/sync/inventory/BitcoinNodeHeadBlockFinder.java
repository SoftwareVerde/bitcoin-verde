package com.softwareverde.bitcoin.server.module.node.sync.inventory;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilter;
import com.softwareverde.bitcoin.server.module.node.sync.BlockFinderHashesBuilder;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class BitcoinNodeHeadBlockFinder {
    public interface Callback {
        void onHeadBlockDetermined(Long blockHeight, Sha256Hash blockHash);
        default void onFailure() { }
    }

    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final ThreadPool _threadPool;
    protected final BanFilter _banFilter;

    public BitcoinNodeHeadBlockFinder(final DatabaseManagerFactory databaseManagerFactory, final ThreadPool threadPool, final BanFilter banFilter) {
        _databaseManagerFactory = databaseManagerFactory;
        _threadPool = threadPool;
        _banFilter = banFilter;
    }

    public void determineHeadBlock(final BitcoinNode bitcoinNode, final Callback callback) {
        final List<Sha256Hash> blockHashes;
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseManager);
            blockHashes = blockFinderHashesBuilder.createBlockHeaderFinderBlockHashes(1);
        }
        catch (final Exception exception) {
            callback.onFailure();
            return;
        }

        final Long bitcoinNodePing = bitcoinNode.getAveragePing();
        final Long maxTimeout = Math.min(Math.max(1000L, bitcoinNodePing), 5000L);
        final AtomicBoolean didRespond = new AtomicBoolean(false);
        final Pin pin = new Pin();

        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    pin.waitForRelease(maxTimeout);
                }
                catch (final Exception exception) { }

                if (! didRespond.get()) {
                    callback.onFailure();
                }
            }
        });

        Logger.trace("Finding Head Block for " + bitcoinNode + ", sending: " + blockHashes.get(0));

        bitcoinNode.requestBlockHeaders(blockHashes, new BitcoinNode.DownloadBlockHeadersCallback() {
            @Override
            public void onResult(final List<BlockHeader> blockHeaders) {
                didRespond.set(true);
                pin.release();

                if (_banFilter != null) {
                    final boolean receivedInvalidHeaders = _banFilter.onHeadersReceived(bitcoinNode, blockHeaders);
                    if (receivedInvalidHeaders) {
                        Logger.info("Received invalid headers from " + bitcoinNode + ".");
                        bitcoinNode.disconnect();
                        callback.onFailure();
                        return;
                    }
                }

                if (blockHeaders.isEmpty()) {
                    Logger.debug("onFailure: " + bitcoinNode + " " + blockHeaders.getCount());
                    callback.onFailure();
                    return;
                }

                final int blockHeaderCount = blockHeaders.getCount();
                final BlockHeader firstBlockHeader = blockHeaders.get(0);
                final BlockHeader lastBlockHeader = blockHeaders.get(blockHeaderCount - 1);

                try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                    final Sha256Hash sharedAncestorBlockHash = firstBlockHeader.getPreviousBlockHash();
                    final BlockId sharedAncestorBlockId = blockHeaderDatabaseManager.getBlockHeaderId(sharedAncestorBlockHash);
                    if (sharedAncestorBlockId == null) {
                        Logger.debug("onFailure: " + bitcoinNode + " " + blockHeaders.getCount());
                        callback.onFailure();
                        return;
                    }

                    final Long sharedAncestorBlockHeight = blockHeaderDatabaseManager.getBlockHeight(sharedAncestorBlockId);
                    final Long maxBlockHeight = (sharedAncestorBlockHeight + blockHeaderCount);
                    final Sha256Hash lastBlockHash = lastBlockHeader.getHash();
                    Logger.trace(bitcoinNode + " head block " + maxBlockHeight + " / " + lastBlockHash);
                    callback.onHeadBlockDetermined(maxBlockHeight, lastBlockHash);
                }
                catch (final Exception exception) {
                    Logger.debug("onFailure: " + bitcoinNode + " " + blockHeaders.getCount());
                    Logger.debug(exception);
                    callback.onFailure();
                }
            }
        });
    }
}

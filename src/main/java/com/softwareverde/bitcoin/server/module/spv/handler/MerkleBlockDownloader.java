package com.softwareverde.bitcoin.server.module.spv.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.spv.SpvBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SpvTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.RequestBabysitter;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.MerkleBlockParameters;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sequentially downloads merkleBlocks received via SpvBlockInventoryMessageCallback::onResult.
 *  Failed blocks are retried 3 times immediately, and up to 21 times total.
 */
public class MerkleBlockDownloader implements BitcoinNode.SpvBlockInventoryAnnouncementHandler {
    public interface Downloader {
        Tuple<RequestId, BitcoinNode> requestMerkleBlock(Sha256Hash blockHash, BitcoinNode.DownloadMerkleBlockCallback callback);
    }

    public interface DownloadCompleteCallback {
        void newMerkleBlockDownloaded(MerkleBlock merkleBlock, List<Transaction> transactions);
    }

    protected final SystemTime _systemTime = new SystemTime();
    protected final SpvDatabaseManagerFactory _databaseManagerFactory;
    protected final Downloader _merkleBlockDownloader;
    protected final ConcurrentLinkedQueue<Sha256Hash> _queuedBlockHashes = new ConcurrentLinkedQueue<>();
    protected final AtomicBoolean _blockIsInFlight = new AtomicBoolean(false);

    protected final RequestBabysitter _requestBabysitter = new RequestBabysitter();

    protected Runnable _merkleBlockProcessedCallback = null;
    protected DownloadCompleteCallback _downloadCompleteCallback = null;
    protected Long _minimumMerkleBlockHeight = 0L;

    protected final AtomicBoolean _isPaused = new AtomicBoolean(true);

    protected final BitcoinNode.DownloadMerkleBlockCallback _onMerkleBlockDownloaded = new BitcoinNode.DownloadMerkleBlockCallback() {
        private final ConcurrentHashMap<Sha256Hash, ConcurrentLinkedDeque<Long>> _failedDownloadTimes = new ConcurrentHashMap<>();

        protected synchronized Boolean _processMerkleBlock(final MerkleBlockParameters merkleBlockParameters) {
            if (merkleBlockParameters == null) { return false; }

            final MerkleBlock merkleBlock = merkleBlockParameters.getMerkleBlock();
            if (merkleBlock == null) { return false; }

            final PartialMerkleTree partialMerkleTree = merkleBlock.getPartialMerkleTree();
            if (partialMerkleTree == null) { return false; }

            final List<Transaction> transactions = merkleBlockParameters.getTransactions();
            if (transactions == null) { return false; }

            if (! merkleBlock.isValid()) {
                Logger.debug("Invalid MerkleBlock received. Discarding. " + merkleBlock.getHash());
                return false;
            }

            for (final Transaction transaction : transactions) {
                final Sha256Hash transactionHash = transaction.getHash();
                if (! merkleBlock.containsTransaction(transactionHash)) {
                    Logger.debug("MerkleBlock did not contain transaction. Block: " + merkleBlock.getHash() + " Tx: " + transactionHash);
                    return false;
                }
            }

//            final UpdateBloomFilterMode updateBloomFilterMode = Util.coalesce(UpdateBloomFilterMode.valueOf(bloomFilter.getUpdateMode()), UpdateBloomFilterMode.READ_ONLY);
//            final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter, updateBloomFilterMode);
//            return transactionBloomFilterMatcher.shouldInclude(transaction);
            try (final SpvDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                final SpvBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
                final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

                TransactionUtil.startTransaction(databaseConnection);
                final Sha256Hash previousBlockHash = merkleBlock.getPreviousBlockHash();
                if (! Util.areEqual(previousBlockHash, Sha256Hash.EMPTY_HASH)) { // Check for Genesis Block...
                    final BlockId previousBlockId = blockHeaderDatabaseManager.getBlockHeaderId(merkleBlock.getPreviousBlockHash());
                    if (previousBlockId == null) {
                        Logger.debug("NOTICE: Out of order MerkleBlock received. Discarding. " + merkleBlock.getHash());
                        return false;
                    }
                }

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(merkleBlock);
                    blockDatabaseManager.storePartialMerkleTree(blockId, partialMerkleTree);

                    for (final Transaction transaction : transactions) {
                        final TransactionId transactionId = transactionDatabaseManager.storeTransaction(transaction);
                        blockDatabaseManager.addTransactionToBlock(blockId, transactionId);
                    }
                }
                TransactionUtil.commitTransaction(databaseConnection);
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
                return false;
            }

            final DownloadCompleteCallback downloadCompleteCallback = _downloadCompleteCallback;
            if (downloadCompleteCallback != null) {
                downloadCompleteCallback.newMerkleBlockDownloaded(merkleBlock, transactions);
            }

            return true;
        }

        /**
         * Returns true if the block download should continue from the normal download mechanism.
         */
        protected synchronized Boolean _onFailure(final Sha256Hash merkleBlockHash) {
            try { Thread.sleep(5000L); } catch (final InterruptedException exception) { return false; }

            final Long now = _systemTime.getCurrentTimeInMilliSeconds();
            ConcurrentLinkedDeque<Long> failedDownloadTimestamps = _failedDownloadTimes.get(merkleBlockHash);
            if (failedDownloadTimestamps == null) {
                failedDownloadTimestamps = new ConcurrentLinkedDeque<>();
                _failedDownloadTimes.put(merkleBlockHash, failedDownloadTimestamps);
            }
            failedDownloadTimestamps.add(now);

            int totalFailureCount = 0;
            int recentFailureCount = 0;
            for (final Long failedTimestamp : failedDownloadTimestamps) {
                if (now - failedTimestamp > 30000L) {
                    recentFailureCount += 1;
                }
                totalFailureCount += 1;
            }

            if (recentFailureCount <= 3) {
                Logger.debug("Retrying Merkle: " + merkleBlockHash);
                _requestMerkleBlock(merkleBlockHash);
                return false;
            }

            if (totalFailureCount <= 21) {
                // TODO: Does sequential-ness matter?
                // Add the block to the back of the stack and try again later...
                Logger.debug("Re-Queueing Merkle for Download: " + merkleBlockHash);
                _queuedBlockHashes.add(merkleBlockHash);
            }

            return true;
        }

        @Override
        public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final MerkleBlockParameters merkleBlockParameters) {
            _processMerkleBlock(merkleBlockParameters);
            _blockIsInFlight.set(false);

            final Runnable merkleBlockProcessedCallback = _merkleBlockProcessedCallback;
            if (merkleBlockProcessedCallback != null) {
                merkleBlockProcessedCallback.run();
            }

            _downloadNextMerkleBlock();
        }

        @Override
        public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash merkleBlockHash) {
            final Boolean shouldDownloadNextBlock = _onFailure(merkleBlockHash);
            if (! shouldDownloadNextBlock) { return; } // Retrying current block...
            _blockIsInFlight.set(false);

            final Runnable merkleBlockProcessedCallback = _merkleBlockProcessedCallback;
            if (merkleBlockProcessedCallback != null) {
                merkleBlockProcessedCallback.run();
            }

            _downloadNextMerkleBlock();
        }
    };

    protected synchronized void _refillBlockHashQueue() {
        try (final SpvDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final SpvBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final List<BlockId> nextBlockIds = blockDatabaseManager.selectNextIncompleteBlocks(_minimumMerkleBlockHeight, 256);
            if (nextBlockIds.isEmpty()) { return; }

            final List<Sha256Hash> blockHashes = blockHeaderDatabaseManager.getBlockHashes(nextBlockIds);
            for (final Sha256Hash blockHash : blockHashes) {
                if (blockHash == null) { continue; }
                _queuedBlockHashes.add(blockHash);
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    public static class WatchedRequest extends RequestBabysitter.WatchedRequest implements BitcoinNode.DownloadMerkleBlockCallback {
        protected RequestId _requestId;
        protected BitcoinNode _bitcoinNode;
        protected final Sha256Hash _blockHash;
        protected final BitcoinNode.DownloadMerkleBlockCallback _callback;

        public WatchedRequest(final Sha256Hash blockHash, final BitcoinNode.DownloadMerkleBlockCallback downloadMerkleBlockCallback) {
            _blockHash = blockHash;
            _callback = downloadMerkleBlockCallback;
        }

        public void setRequestInformation(final RequestId requestId, final BitcoinNode bitcoinNode) {
            _requestId = requestId;
            _bitcoinNode = bitcoinNode;
        }

        @Override
        public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final MerkleBlockParameters result) {
            if (! this.onWatchEnded()) { return; }
            _callback.onResult(requestId, bitcoinNode, result);
        }

        @Override
        protected void onExpiration() {
            _callback.onFailure(_requestId, _bitcoinNode, _blockHash);
        }

        @Override
        public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
            if (! this.onWatchEnded()) { return; }
            _callback.onFailure(requestId, bitcoinNode, blockHash);
        }
    }

    protected void _requestMerkleBlock(final Sha256Hash blockHash) {
        Logger.debug("Downloading Merkle Block: " + blockHash);

        final WatchedRequest watchedRequest = new WatchedRequest(blockHash, _onMerkleBlockDownloaded);
        final Tuple<RequestId, BitcoinNode> requestInformation = _merkleBlockDownloader.requestMerkleBlock(blockHash, watchedRequest);
        watchedRequest.setRequestInformation(requestInformation.first, requestInformation.second);
        _requestBabysitter.watch(watchedRequest, 15L);
    }

    protected synchronized void _downloadNextMerkleBlock() {
        if (_isPaused.get()) { return; }
        if (! _blockIsInFlight.compareAndSet(false, true)) { return; }

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            while (true) {
                final Sha256Hash blockHash = _queuedBlockHashes.poll();
                if (blockHash == null) {
                    _refillBlockHashQueue();

                    if (_queuedBlockHashes.isEmpty()) { break; }
                    else { continue; }
                }

                if (! blockDatabaseManager.hasTransactions(blockHash)) {
                    _requestMerkleBlock(blockHash);
                    return;
                }
                else {
                    Logger.debug("Skipping MerkleBlock. Block already downloaded: " + blockHash);
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        // No block ended up being requested...
        _blockIsInFlight.set(false);
    }

    public MerkleBlockDownloader(final SpvDatabaseManagerFactory databaseManagerFactory, final Downloader downloader) {
        _databaseManagerFactory = databaseManagerFactory;
        _merkleBlockDownloader = downloader;
    }

    public void setMinimumMerkleBlockHeight(final Long minimumMerkleBlockHeight) {
        _minimumMerkleBlockHeight = minimumMerkleBlockHeight;
    }

    public void resetQueue() {
        _queuedBlockHashes.clear();
        _refillBlockHashQueue();
    }

    public void pause() {
        _isPaused.set(true);
    }

    public void start() {
        // if (! _isPaused.compareAndSet(true, false)) { return; }
        _requestBabysitter.start();
        _isPaused.set(false);
        _downloadNextMerkleBlock();
    }

    public void setDownloadCompleteCallback(final DownloadCompleteCallback downloadCompleteCallback) {
        _downloadCompleteCallback = downloadCompleteCallback;
    }

    public void setMerkleBlockProcessedCallback(final Runnable merkleBlockProcessedCallback) {
        _merkleBlockProcessedCallback = merkleBlockProcessedCallback;
    }

    @Override
    public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
        for (final Sha256Hash blockHash : blockHashes) {
            Logger.debug("Queuing Merkle Block for download: " + blockHash);
            _queuedBlockHashes.add(blockHash);
        }

        _downloadNextMerkleBlock();
    }

    public void wakeUp() {
        _downloadNextMerkleBlock();
    }

    public Boolean isRunning() {
        return _blockIsInFlight.get();
    }

    public void shutdown() {
        _requestBabysitter.stop();
    }
}

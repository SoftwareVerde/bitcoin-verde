package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.WorkerManager;
import com.softwareverde.util.Promise;
import com.softwareverde.util.Tuple;

public class BlockLoader {
    protected static class Jerb {
        public final long blockHeight;
        public final Sha256Hash blockHash;
        public Promise<Block> promise;
        public BitcoinNode bitcoinNode;

        public Jerb(final long blockHeight, final Sha256Hash blockHash) {
            this.blockHeight = blockHeight;
            this.blockHash = blockHash;
        }

        public Jerb(final long blockHeight, final Sha256Hash blockHash, final Promise<Block> promise) {
            this.blockHeight = blockHeight;
            this.blockHash = blockHash;
            this.promise = promise;
        }
    }

    protected final BlockInflater _blockInflater = new BlockInflater();
    protected final Blockchain _blockchain;
    protected final PendingBlockStore _blockStore;
    protected final WorkerManager _workerManager;
    protected final Jerb[] _jerbs;
    protected final MutableList<BitcoinNode> _bitcoinNodes = new MutableArrayList<>(32);
    protected final long _maxTimeout = 15000L;

    protected long _blockHeight; // exclusive
    protected int _jitter = 0;

    protected int _getJerbIndex(final long blockHeight) {
        return (int) (blockHeight % _jerbs.length);
    }

    protected Tuple<BitcoinNode, Promise<Block>> _requestBlock(final Sha256Hash blockHash) {
        synchronized (_bitcoinNodes) {
            final int nodeCount = _bitcoinNodes.getCount();
            if (nodeCount == 0) { return null; }

            for (int i = 0; i < nodeCount; ++i) {
                _jitter += 1;
                final int nodeIndex = _jitter % nodeCount;

                final BitcoinNode bitcoinNode = _bitcoinNodes.get(nodeIndex);
                if ( (bitcoinNode == null) || (! bitcoinNode.isConnected())) { continue; }
                if (bitcoinNode.getPendingBlockRequests().getCount() > 0) { continue; }

                final Promise<Block> promise = new Promise<>();
                bitcoinNode.requestBlock(blockHash, new BitcoinNode.DownloadBlockCallback() {
                    @Override
                    public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                        promise.setResult(block);
                    }

                    @Override
                    public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash response) {
                        promise.setResult(null);
                    }
                });
                return new Tuple<>(bitcoinNode, promise);
            }
        }

        return null;
    }

    protected Jerb _createJerb(final long blockHeight, final Sha256Hash blockHash) {
        final Jerb jerb = new Jerb(blockHeight, blockHash);
        jerb.promise = _workerManager.submitJob(new WorkerManager.UnsafeJob<>() {
            @Override
            public Block run() throws Exception {
                if (_blockStore.pendingBlockExists(blockHash)) {
                    final ByteArray blockData = _blockStore.getPendingBlockData(blockHash);
                    if (blockData != null) {
                        final Block block = _blockInflater.fromBytes(blockData);
                        if (block != null) {
                            return block;
                        }
                    }
                }

                final Tuple<BitcoinNode, Promise<Block>> request = _requestBlock(blockHash);
                if (request == null) { return null; }

                jerb.bitcoinNode = request.first;
                return request.second.getResult();
            }
        });
        return jerb;
    }

    protected void _fillQueue() {
        for (int n = 0; n < _jerbs.length; ++n) {
            final long blockHeight = _blockHeight + 1L + n;
            final int i = _getJerbIndex(blockHeight);

            final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
            if (blockHeader == null) { break; }
            final Sha256Hash blockHash = blockHeader.getHash();

            _jerbs[i] = _createJerb(blockHeight, blockHash);
        }
    }

    public BlockLoader(final Blockchain blockchain, final PendingBlockStore blockStore) {
        _blockchain = blockchain;
        _blockStore = blockStore;

        final int queueCount = 32;
        _workerManager = new WorkerManager(4, queueCount);
        _jerbs = new Jerb[queueCount];
    }

    public void open() throws Exception {
        _blockHeight = _blockchain.getHeadBlockHeight();
        _workerManager.start();

        _fillQueue();
    }

    public void close() throws Exception {
        _workerManager.close();
    }

    public void addNode(final BitcoinNode bitcoinNode) {
        synchronized (_bitcoinNodes) {
            final int nodeCount = _bitcoinNodes.getCount();
            for (int i = 0; i < nodeCount; ++i) {
                final BitcoinNode existingBitcoinNode = _bitcoinNodes.get(i);
                if ( (existingBitcoinNode == null) || (! existingBitcoinNode.isConnected()) ) {
                    _bitcoinNodes.set(i, bitcoinNode);
                    System.out.println(i + "=" + bitcoinNode);
                    return;
                }
            }
            _bitcoinNodes.add(bitcoinNode);
            System.out.println("added: " + bitcoinNode);
        }
    }

    public Block getNextBlock() throws Exception {
        final long blockHeight = _blockHeight + 1L;
        final int i = _getJerbIndex(blockHeight);
        final Jerb jerb = _jerbs[i];
        if (jerb == null) { return null; }

        final Block block = jerb.promise.getResult(_maxTimeout);

        if (block != null) {
            final long nextBlockHeight = blockHeight + _jerbs.length;
            final BlockHeader blockHeader = _blockchain.getBlockHeader(nextBlockHeight);
            if (blockHeader == null) {
                _jerbs[i] = null;
            }
            else {
                final Sha256Hash nextBlockHash = blockHeader.getHash();
                _jerbs[i] = _createJerb(nextBlockHeight, nextBlockHash);
            }

            _blockHeight += 1L;
            return block;
        }
        else {
            _jerbs[i] = _createJerb(jerb.blockHeight, jerb.blockHash);
            return null;
        }
    }
}

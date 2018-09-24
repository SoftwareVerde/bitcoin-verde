package com.softwareverde.bitcoin.server.module.node.sync.blockqueue;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * A thread-safe queue of Blocks.
 *  Each Block retrieved is guaranteed to a descendant of the previous block.
 */
public class BlockQueue {
    protected final Object _mutex = new Object();
    protected final LinkedList<Block> _blocks = new LinkedList<Block>();
    protected final Runnable _queueEmptiedCallback;
    protected Sha256Hash _lastBlockHash = null;

    protected final RotatingQueue<Block> _outOfOrderBlocks = new RotatingQueue<Block>(8);

    public BlockQueue() {
        _queueEmptiedCallback = null;
    }

    public BlockQueue(final Runnable queueEmptiedCallback) {
        _queueEmptiedCallback = queueEmptiedCallback;
    }

    public Integer getSize() {
        synchronized (_mutex) {
            return _blocks.size();
        }
    }

    public void addBlock(final Block block) {
        final Sha256Hash blockHash = block.getHash();
        final Sha256Hash blockPreviousBlockHash = block.getPreviousBlockHash();

        synchronized (_mutex) {
            if (_lastBlockHash != null) {
                // Add the block to the out-of-order queue if the block's hash is not where the queue left off...
                // NOTE: Using _lastBlockHash instead of _blocks.getLast().getHash() ensures the order is maintained even after the blockQueue is emptied.
                if (! Util.areEqual(_lastBlockHash, blockPreviousBlockHash)) {
                    _outOfOrderBlocks.add(block);
                    return;
                }
            }

            _blocks.addLast(block);
            _lastBlockHash = blockHash;

            { // Enqueue the out-of-order blocks that are (now) in-order...
                Iterator<Block> iterator = _outOfOrderBlocks.iterator();
                while (iterator.hasNext()) {
                    final Block outOfOrderBlock = iterator.next();

                    final Sha256Hash outOfOrderBlockPreviousBlockHash = outOfOrderBlock.getPreviousBlockHash();

                    if (Util.areEqual(_lastBlockHash, outOfOrderBlockPreviousBlockHash)) {
                        final Sha256Hash outOfOrderBlockHash = outOfOrderBlock.getHash();

                        _blocks.addLast(outOfOrderBlock);
                        _lastBlockHash = outOfOrderBlockHash;

                        // Restart the search through the out-of-order blocks...
                        iterator.remove();
                        iterator = _outOfOrderBlocks.iterator();
                    }
                }
            }
        }
    }

    public Block getNextBlock() {
        synchronized (_mutex) {
            if (_blocks.isEmpty()) { return null; }

            final Block block = _blocks.removeFirst();

            if (_blocks.isEmpty()) {
                if (_queueEmptiedCallback != null) {
                    (new Thread(_queueEmptiedCallback)).start();
                }
            }

            return block;
        }
    }

    public void startAfterBlock(final Sha256Hash blockHash) {
        synchronized (_mutex) {
            _lastBlockHash = blockHash;
            _blocks.clear();
        }
    }

    public Boolean isEmpty() {
        synchronized (_mutex) {
            return _blocks.isEmpty();
        }
    }

    public void clear() {
        synchronized (_mutex) {
            _blocks.clear();
            _outOfOrderBlocks.clear();
            _lastBlockHash = null;
        }
    }
}

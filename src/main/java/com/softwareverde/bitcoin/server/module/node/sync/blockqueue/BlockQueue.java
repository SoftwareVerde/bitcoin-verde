package com.softwareverde.bitcoin.server.module.node.sync.blockqueue;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

import java.util.LinkedList;

/**
 * A thread-safe queue of Blocks.
 *  Each Block retrieved is guaranteed to a descendant of the previous block.
 */
public class BlockQueue {
    protected final Object _mutex = new Object();
    protected final LinkedList<Block> _blocks = new LinkedList<Block>();

    public Integer getSize() {
        synchronized (_mutex) {
            return _blocks.size();
        }
    }

    public void addBlock(final Block block) {
        synchronized (_mutex) {
            if (! _blocks.isEmpty()) {
                final Block lastBlock = _blocks.getLast();
                final Sha256Hash lastBlockHash = lastBlock.getHash();

                if (! Util.areEqual(block.getPreviousBlockHash(), lastBlockHash)) {
                    return;
                }
            }

            _blocks.addLast(block);
        }
    }

    public Block getNextBlock() {
        synchronized (_mutex) {
            if (_blocks.isEmpty()) { return null; }

            final Block block = _blocks.removeFirst();
            return block;
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
        }
    }
}

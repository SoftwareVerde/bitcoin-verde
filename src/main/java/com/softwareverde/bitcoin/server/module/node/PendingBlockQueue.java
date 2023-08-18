package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Promise;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

public class PendingBlockQueue {
    protected final CircleBuffer<Tuple<Long, Promise<Block>>> _availableBlocks;

    public PendingBlockQueue(final int queueCount) {
        _availableBlocks = new CircleBuffer<>(queueCount);
    }

    public synchronized void addBlock(final Long blockHeight, final Promise<Block> block) {
        final Tuple<Long, Promise<Block>> entry = new Tuple<>(blockHeight, block);
        _availableBlocks.push(entry);
    }

    public synchronized boolean containsBlock(final Long blockHeight) {
        for (final Tuple<Long, Promise<Block>> entry : _availableBlocks) {
            if ( Util.areEqual(blockHeight, entry.first) && (entry.second != null) ) {
                return true;
            }
        }
        return false;
    }

    public synchronized Promise<Block> getBlock(final Long blockHeight) {
        for (final Tuple<Long, Promise<Block>> entry : _availableBlocks) {
            if ( Util.areEqual(blockHeight, entry.first) && (entry.second != null) ) {
                final Promise<Block> promise = entry.second;
                entry.second = null;
                return promise;
            }
        }
        return null;
    }

    public void clear() {
        _availableBlocks.clear();
    }
}

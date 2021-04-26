package com.softwareverde.bitcoin.server.module.node.sync.inventory;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.CircleBuffer;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BitcoinNodeBlockInventoryTracker {
    protected final Integer _bufferItemCountPerNode = 2048;
    protected final WeakHashMap<BitcoinNode, CircleBuffer<Sha256Hash>> _blockInventory = new WeakHashMap<>();

    protected final ReentrantReadWriteLock.ReadLock _inventoryReadLock;
    protected final ReentrantReadWriteLock.WriteLock _inventoryWriteLock;

    public BitcoinNodeBlockInventoryTracker() {
        final ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
        _inventoryReadLock = reentrantReadWriteLock.readLock();
        _inventoryWriteLock = reentrantReadWriteLock.writeLock();
    }

    public void markInventoryAvailable(final Sha256Hash blockHash, final BitcoinNode bitcoinNode) {
        _inventoryWriteLock.lock();
        try {
            final CircleBuffer<Sha256Hash> blockInventory;
            {
                final CircleBuffer<Sha256Hash> existingInventoryBuffer = _blockInventory.get(bitcoinNode);
                if (existingInventoryBuffer == null) {
                    blockInventory = new CircleBuffer<>(_bufferItemCountPerNode);
                    _blockInventory.put(bitcoinNode, blockInventory);
                }
                else {
                    blockInventory = existingInventoryBuffer;
                }
            }

            blockInventory.push(blockHash);
        }
        finally {
            _inventoryWriteLock.unlock();
        }
    }

    public List<BitcoinNode> getNodesWithInventory(final Sha256Hash blockHash) {
        _inventoryReadLock.lock();
        try {
            final MutableList<BitcoinNode> bitcoinNodes = new MutableList<>();
            synchronized (_blockInventory) {
                for (final Map.Entry<BitcoinNode, CircleBuffer<Sha256Hash>> entry : _blockInventory.entrySet()) {
                    final CircleBuffer<Sha256Hash> availableBlockHashes = entry.getValue();
                    if (availableBlockHashes.contains(blockHash)) {
                        final BitcoinNode bitcoinNode = entry.getKey();
                        bitcoinNodes.add(bitcoinNode);
                    }
                }
            }
            return bitcoinNodes;
        }
        finally {
            _inventoryReadLock.unlock();
        }
    }
}

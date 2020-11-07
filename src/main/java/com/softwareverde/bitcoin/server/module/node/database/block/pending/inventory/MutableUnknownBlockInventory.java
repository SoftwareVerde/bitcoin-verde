package com.softwareverde.bitcoin.server.module.node.database.block.pending.inventory;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class MutableUnknownBlockInventory implements UnknownBlockInventory {
    protected Sha256Hash _previousBlockHash;
    protected Sha256Hash _firstUnknownBlockHash;
    protected Sha256Hash _lastUnknownBlockHash;

    public MutableUnknownBlockInventory() {
        this(null, null, null);
    }

    public MutableUnknownBlockInventory(final Sha256Hash firstUnknownBlockHash, final Sha256Hash lastUnknownBlockHash) {
        this(null, firstUnknownBlockHash, lastUnknownBlockHash);
    }

    public MutableUnknownBlockInventory(final Sha256Hash previousBlockHash, final Sha256Hash requestedBlockHash, final Sha256Hash stopAtBlockHash) {
        _previousBlockHash = previousBlockHash;
        _firstUnknownBlockHash = requestedBlockHash;
        _lastUnknownBlockHash = stopAtBlockHash;
    }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _previousBlockHash = previousBlockHash;
    }

    public void setFirstUnknownBlockHash(final Sha256Hash firstUnknownBlockHash) {
        _firstUnknownBlockHash = firstUnknownBlockHash;
    }

    public void setLastUnknownBlockHash(final Sha256Hash lastUnknownBlockHash) {
        _lastUnknownBlockHash = lastUnknownBlockHash;
    }

    @Override
    public Sha256Hash getPreviousBlockHash() {
        return _previousBlockHash;
    }

    @Override
    public Sha256Hash getFirstUnknownBlockHash() {
        return _previousBlockHash;
    }

    @Override
    public Sha256Hash getLastUnknownBlockHash() {
        return _lastUnknownBlockHash;
    }
}

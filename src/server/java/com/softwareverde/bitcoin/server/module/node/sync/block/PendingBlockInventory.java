package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.lang.ref.WeakReference;

public class PendingBlockInventory {
    public final Long priority;
    public final Sha256Hash blockHash;
    public final Long blockHeight;

    public final WeakReference<BitcoinNode> bitcoinNode;

    public PendingBlockInventory(final Long priority, final Sha256Hash blockHash, final BitcoinNode bitcoinNode) {
        this(priority, blockHash, null, bitcoinNode);
    }

    public PendingBlockInventory(final Long priority, final Sha256Hash blockHash, final Long blockHeight) {
        this(priority, blockHash, blockHeight, null);
    }

    public PendingBlockInventory(final Long priority, final Sha256Hash blockHash, final Long blockHeight, final BitcoinNode bitcoinNode) {
        this.priority = priority;
        this.blockHash = blockHash;
        this.blockHeight = blockHeight;

        this.bitcoinNode = new WeakReference<>(bitcoinNode);
    }
}
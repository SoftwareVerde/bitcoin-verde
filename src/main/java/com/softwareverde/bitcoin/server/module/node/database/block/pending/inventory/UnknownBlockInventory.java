package com.softwareverde.bitcoin.server.module.node.database.block.pending.inventory;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface UnknownBlockInventory {
    Sha256Hash getPreviousBlockHash();
    Sha256Hash getFirstUnknownBlockHash();
    Sha256Hash getLastUnknownBlockHash();
}

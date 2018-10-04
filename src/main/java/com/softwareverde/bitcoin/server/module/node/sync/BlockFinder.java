package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;

public class BlockFinder {
    protected final List<Sha256Hash> _blockHashes;

    public BlockFinder(final List<Sha256Hash> blockFinderHashes) {
        _blockHashes = blockFinderHashes.asConst();
    }

    public List<Sha256Hash> getBlockHashes() {
        return _blockHashes;
    }
}

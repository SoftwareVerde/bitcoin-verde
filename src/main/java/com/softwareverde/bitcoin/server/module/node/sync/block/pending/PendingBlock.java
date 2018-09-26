package com.softwareverde.bitcoin.server.module.node.sync.block.pending;

import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.bytearray.ByteArray;

public class PendingBlock {
    protected final Sha256Hash _blockHash;
    protected final Sha256Hash _previousBlockHash;
    protected final ByteArray _data;

    public PendingBlock(final Sha256Hash blockHash) {
        _blockHash = blockHash;
        _previousBlockHash = null;
        _data = null;
    }

    public PendingBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash, final ByteArray blockData) {
        _blockHash = blockHash;
        _previousBlockHash =  previousBlockHash;
        _data = blockData;
    }

    public Sha256Hash getBlockHash() { return _blockHash; }

    public Sha256Hash getPreviousBlockHash() { return _previousBlockHash; }

    public ByteArray getData() { return _data; }
}

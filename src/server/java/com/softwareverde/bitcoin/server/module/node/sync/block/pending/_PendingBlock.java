package com.softwareverde.bitcoin.server.module.node.sync.block.pending;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class _PendingBlock {
    protected final Sha256Hash _blockHash;
    protected final Sha256Hash _previousBlockHash;
    protected final ByteArray _data;
    protected Block _inflatedBlock;

    public _PendingBlock(final Sha256Hash blockHash) {
        _blockHash = blockHash;
        _previousBlockHash = null;
        _data = null;
        _inflatedBlock = null;
    }

    public _PendingBlock(final Sha256Hash blockHash, final Sha256Hash previousBlockHash, final ByteArray blockData) {
        _blockHash = blockHash;
        _previousBlockHash =  previousBlockHash;
        _data = blockData;
        _inflatedBlock = null;
    }

    public Sha256Hash getBlockHash() { return _blockHash; }

    public Sha256Hash getPreviousBlockHash() { return _previousBlockHash; }

    public ByteArray getData() { return _data; }

    public Block inflateBlock(final BlockInflater blockInflater) {
        if (_inflatedBlock == null) {
            _inflatedBlock = blockInflater.fromBytes(_data);
        }

        return _inflatedBlock;
    }

    public Block getInflatedBlock() {
        return _inflatedBlock;
    }
}

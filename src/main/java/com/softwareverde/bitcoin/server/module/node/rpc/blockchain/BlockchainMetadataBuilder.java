package com.softwareverde.bitcoin.server.module.node.rpc.blockchain;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;

public class BlockchainMetadataBuilder {
    protected BlockchainMetadata _blockchainMetadata = null;

    protected void _ensureObjectExists() {
        if (_blockchainMetadata == null) {
            _blockchainMetadata = new BlockchainMetadata();
        }
    }

    public void setBlockchainSegmentId(final BlockchainSegmentId blockchainSegmentId) {
        _ensureObjectExists();
        _blockchainMetadata._blockchainSegmentId = blockchainSegmentId;
    }

    public void setParentBlockchainSegmentId(final BlockchainSegmentId blockchainSegmentId) {
        _ensureObjectExists();
        _blockchainMetadata._parentBlockchainSegmentId = blockchainSegmentId;
    }

    public void setNestedSet(final Integer nestedSetLeft, final Integer nestedSetRight) {
        _ensureObjectExists();
        _blockchainMetadata._nestedSetLeft = nestedSetLeft;
        _blockchainMetadata._nestedSetRight = nestedSetRight;
    }

    public void setBlockCount(final Long blockCount) {
        _ensureObjectExists();
        _blockchainMetadata._blockCount = blockCount;
    }

    public void setBlockHeight(final Long minBlockHeight, final Long maxBlockHeight) {
        _ensureObjectExists();
        _blockchainMetadata._minBlockHeight = minBlockHeight;
        _blockchainMetadata._maxBlockHeight = maxBlockHeight;
    }

    public BlockchainMetadata build() {
        final BlockchainMetadata blockchainMetadata = _blockchainMetadata;
        _blockchainMetadata = null;
        return blockchainMetadata;
    }
}

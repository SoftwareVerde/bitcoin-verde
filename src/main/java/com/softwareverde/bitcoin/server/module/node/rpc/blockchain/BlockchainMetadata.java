package com.softwareverde.bitcoin.server.module.node.rpc.blockchain;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

public class BlockchainMetadata implements Const, Jsonable {
    protected BlockchainSegmentId _blockchainSegmentId;
    protected BlockchainSegmentId _parentBlockchainSegmentId;
    protected Integer _nestedSetLeft;
    protected Integer _nestedSetRight;
    protected Long _blockCount;
    protected Long _minBlockHeight;
    protected Long _maxBlockHeight;

    public BlockchainSegmentId getBlockchainSegmentId() { return _blockchainSegmentId; }
    public BlockchainSegmentId getParentBlockchainSegmentId() { return _parentBlockchainSegmentId; }
    public Integer getNestedSetLeft() { return _nestedSetLeft; }
    public Integer getNestedSetRight() { return _nestedSetRight; }
    public Long getBlockCount() { return _blockCount; }
    public Long getMinBlockHeight() { return _minBlockHeight; }
    public Long getMaxBlockHeight() { return _maxBlockHeight; }

    @Override
    public Json toJson() {
        final Json json = new Json();
        json.put("blockchainSegmentId", _blockchainSegmentId);
        json.put("parentBlockchainSegmentId", _parentBlockchainSegmentId);
        json.put("nestedSetLeft", _nestedSetLeft);
        json.put("nestedSetRight", _nestedSetRight);
        json.put("blockCount", _blockCount);
        json.put("minBlockHeight", _minBlockHeight);
        json.put("maxBlockHeight", _maxBlockHeight);
        return json;
    }
}

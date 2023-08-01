package com.softwareverde.bitcoin.server.module.node.manager.health;

import com.softwareverde.constable.Const;
import com.softwareverde.network.p2p.node.NodeId;

public class ImmutableNodeHealth implements NodeHealth, Const {
    protected final NodeId _nodeId;
    protected final Long _health;

    public ImmutableNodeHealth(final NodeId nodeId, final Long health) {
        _nodeId = nodeId;
        _health = health;
    }

    @Override
    public NodeId getNodeId() {
        return _nodeId;
    }

    @Override
    public Long getHealth() {
        return _health;
    }

    @Override
    public ImmutableNodeHealth asConst() {
        return this;
    }
}

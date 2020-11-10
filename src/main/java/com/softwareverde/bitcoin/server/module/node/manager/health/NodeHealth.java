package com.softwareverde.bitcoin.server.module.node.manager.health;

import com.softwareverde.constable.Constable;
import com.softwareverde.network.p2p.node.NodeId;

import java.util.Comparator;

public interface NodeHealth extends Constable<ImmutableNodeHealth> {
    interface Request { }

    Long FULL_HEALTH = 24L * 60L * 60L * 1000L;
    Float REGEN_TO_REQUEST_TIME_RATIO = 0.5F;

    Comparator<NodeHealth> HEALTH_ASCENDING_COMPARATOR = new Comparator<NodeHealth>() {
        @Override
        public int compare(final NodeHealth nodeHealth0, final NodeHealth nodeHealth1) {
            final Long nodeHealth0Value = nodeHealth0.getHealth();
            final Long nodeHealth1Value = nodeHealth1.getHealth();
            return (nodeHealth0Value.compareTo(nodeHealth1Value));
        }
    };

    NodeId getNodeId();
    Long getHealth();

    @Override
    ImmutableNodeHealth asConst();
}

package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.server.node.Node;

import java.util.HashMap;
import java.util.Map;

public class NodeTracker {
    private static class NodeTrackerInstance {
        static final NodeTracker instance = new NodeTracker();
    }

    public static NodeTracker getInstance() {
        return NodeTrackerInstance.instance;
    }

    protected final Map<Long, Node> _nodeList = new HashMap<Long, Node>();

    public void addNode(final Node node) {
        _nodeList.put(node.getId(), node);
    }

    public Node getNode(final Long nodeId) {
        return _nodeList.get(nodeId);
    }
}

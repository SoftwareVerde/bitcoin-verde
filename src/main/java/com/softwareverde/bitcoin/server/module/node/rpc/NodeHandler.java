package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.server.module.node.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;
import com.softwareverde.bitcoin.server.module.node.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.util.ConstUtil;
import com.softwareverde.network.p2p.node.Node;

public class NodeHandler implements JsonRpcSocketServerHandler.NodeHandler {
    protected final BitcoinNodeManager _nodeManager;
    protected final NodeInitializer _nodeInitializer;

    public NodeHandler(final BitcoinNodeManager nodeNodeManager, final NodeInitializer nodeInitializer) {
        _nodeManager = nodeNodeManager;
        _nodeInitializer = nodeInitializer;
    }

    @Override
    public Boolean addNode(final String host, final Integer port) {
        if ( (host == null) || (port == null) ) { return false; }
        if ( (port <= 0)    || (port > 65535) ) { return false; }

        final BitcoinNode bitcoinNode = _nodeInitializer.initializeNode(host, port);
        _nodeManager.addNode(bitcoinNode);

        return true;
    }

    @Override
    public List<Node> getNodes() {
        return ConstUtil.downcastList(_nodeManager.getNodes());
    }
}

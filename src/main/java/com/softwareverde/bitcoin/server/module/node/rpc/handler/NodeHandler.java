package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.network.ip.Ip;

public class NodeHandler implements NodeRpcHandler.NodeHandler {
    protected final BitcoinNodeManager _nodeManager;
    protected final NodeInitializer _nodeInitializer;

    public NodeHandler(final BitcoinNodeManager nodeNodeManager, final NodeInitializer nodeInitializer) {
        _nodeManager = nodeNodeManager;
        _nodeInitializer = nodeInitializer;
    }

    @Override
    public void addNode(final Ip ip, final Integer port) {
        if ( (ip == null) || (port == null) ) { return; }
        if ( (port <= 0) || (port > 65535) ) { return; }

        final String ipString = ip.toString();

        final BitcoinNode bitcoinNode = _nodeInitializer.initializeNode(ipString, port);
        _nodeManager.addNode(bitcoinNode);
    }

    @Override
    public List<BitcoinNode> getNodes() {
        return _nodeManager.getNodes();
    }

    @Override
    public void banNode(final Ip ip) {
        _nodeManager.banNode(ip);
    }

    @Override
    public void unbanNode(final Ip ip) {
        _nodeManager.unbanNode(ip);
    }
}

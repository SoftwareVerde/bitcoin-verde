package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.network.socket.BinarySocket;

public class NodeInitializer {
    protected final BitcoinNode.QueryBlocksCallback _queryBlocksCallback;
    protected final BitcoinNode.QueryBlockHeadersCallback _queryBlockHeadersCallback;
    protected final BitcoinNode.RequestDataCallback _requestDataCallback;

    public NodeInitializer(final BitcoinNode.QueryBlocksCallback queryBlocksCallback, final BitcoinNode.QueryBlockHeadersCallback queryBlockHeadersCallback, final BitcoinNode.RequestDataCallback requestDataCallback) {
        _queryBlocksCallback = queryBlocksCallback;
        _queryBlockHeadersCallback = queryBlockHeadersCallback;
        _requestDataCallback = requestDataCallback;
    }

    public BitcoinNode initializeNode(final String host, final Integer port) {
        final BitcoinNode node = new BitcoinNode(host, port);
        node.setQueryBlocksCallback(_queryBlocksCallback);
        node.setQueryBlockHeadersCallback(_queryBlockHeadersCallback);
        node.setRequestDataCallback(_requestDataCallback);
        return node;
    }

    public BitcoinNode initializeNode(final BinarySocket binarySocket) {
        final BitcoinNode node = new BitcoinNode(binarySocket);
        node.setQueryBlocksCallback(_queryBlocksCallback);
        node.setQueryBlockHeadersCallback(_queryBlockHeadersCallback);
        node.setRequestDataCallback(_requestDataCallback);
        return node;
    }
}

package com.softwareverde.bitcoin.server.node.request;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;

public class UnfulfilledRequest {
    public final BitcoinNode bitcoinNode;
    public final RequestId requestId;
    public final RequestPriority requestPriority;

    public UnfulfilledRequest(final BitcoinNode bitcoinNode, final RequestId requestId, final RequestPriority requestPriority) {
        this.bitcoinNode = bitcoinNode;
        this.requestId = requestId;
        this.requestPriority = requestPriority;
    }
}

package com.softwareverde.bitcoin.server.node.request;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class UnfulfilledSha256HashRequest extends UnfulfilledRequest {
    public final Sha256Hash hash;

    public UnfulfilledSha256HashRequest(final BitcoinNode bitcoinNode, final RequestId requestId, final RequestPriority requestPriority, final Sha256Hash hash) {
        super(bitcoinNode, requestId, requestPriority);
        this.hash = hash;
    }
}

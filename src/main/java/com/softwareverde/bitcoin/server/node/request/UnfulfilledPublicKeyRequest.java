package com.softwareverde.bitcoin.server.node.request;

import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;

public class UnfulfilledPublicKeyRequest extends UnfulfilledRequest {
    public final PublicKey publicKey;

    public UnfulfilledPublicKeyRequest(final BitcoinNode bitcoinNode, final RequestId requestId, final RequestPriority requestPriority, final PublicKey publicKey) {
        super(bitcoinNode, requestId, requestPriority);
        this.publicKey = publicKey;
    }
}


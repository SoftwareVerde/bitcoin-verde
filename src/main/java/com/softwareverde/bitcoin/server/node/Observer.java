package com.softwareverde.bitcoin.server.node;

public interface Observer {
    default void onFailedRequest(BitcoinNode bitcoinNode, FailableRequest request) { }
}

package com.softwareverde.network.p2p.node;

public interface NodeFactory<T extends Node> {
    T newNode(String host, Integer port);
}

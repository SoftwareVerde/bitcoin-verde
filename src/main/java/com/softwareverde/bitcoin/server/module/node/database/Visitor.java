package com.softwareverde.bitcoin.server.module.node.database;

public interface Visitor<T> {
    void run(T value) throws Exception;
}

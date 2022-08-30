package com.softwareverde.bitcoin.server.module.node.database;

public interface Visitor<T> {
    void visit(T item) throws Exception;
}

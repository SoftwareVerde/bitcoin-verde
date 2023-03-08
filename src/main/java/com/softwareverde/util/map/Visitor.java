package com.softwareverde.util.map;

public interface Visitor<T> {
    void visit(T item) throws Exception;
}

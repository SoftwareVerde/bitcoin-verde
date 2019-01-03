package com.softwareverde.util;

public class Tuple<S, T> {
    public S first;
    public T second;

    public Tuple() { }

    public Tuple(final S first, final T second) {
        this.first = first;
        this.second = second;
    }
}

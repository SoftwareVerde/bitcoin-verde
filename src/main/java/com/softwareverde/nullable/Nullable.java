package com.softwareverde.nullable;

public class Nullable<T> {
    public static <T> Nullable<T> Null() {
        return new Nullable<T>(null);
    }

    public static <T> Nullable<T> wrap(final T value) {
        return new Nullable<T>(value);
    }

    public final T value;

    public Nullable(final T value) {
        this.value = value;
    }

    public Boolean isNull() {
        return (this.value == null);
    }

    public T getValue() {
        if (this.value == null) { throw new NullPointerException("Attempting to get value from empty Nullable."); }
        return this.value;
    }
}

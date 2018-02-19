package com.softwareverde.bitcoin.type.callback;

public interface Callback<T> {
    void onResult(T result);
}

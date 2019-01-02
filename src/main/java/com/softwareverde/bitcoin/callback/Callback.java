package com.softwareverde.bitcoin.callback;

public interface Callback<T> {
    void onResult(T result);
}

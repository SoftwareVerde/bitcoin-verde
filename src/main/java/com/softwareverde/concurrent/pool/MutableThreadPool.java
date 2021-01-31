package com.softwareverde.concurrent.pool;

public interface MutableThreadPool extends ThreadPool {
    void start();
    void stop();
}

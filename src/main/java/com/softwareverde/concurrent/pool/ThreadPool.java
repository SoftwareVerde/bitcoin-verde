package com.softwareverde.concurrent.pool;

public interface ThreadPool {
    void execute(Runnable runnable);
}

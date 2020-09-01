package com.softwareverde.bitcoin.context;

import com.softwareverde.concurrent.pool.ThreadPool;

public interface ThreadPoolContext {
    ThreadPool getThreadPool();
}

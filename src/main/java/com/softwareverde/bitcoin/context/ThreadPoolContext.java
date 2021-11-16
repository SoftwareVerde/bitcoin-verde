package com.softwareverde.bitcoin.context;

import com.softwareverde.concurrent.threadpool.ThreadPool;

public interface ThreadPoolContext {
    ThreadPool getThreadPool();
}

package com.softwareverde.bitcoin.block.validator.thread;

public interface TaskHandler<T, S> {
    void init();

    /**
     * TaskHandler.executeTask() is invoked an arbitrary number of unique times by the same thread.
     *  Each invocation should perform its task and update its internal (as necessary), in preparation for a call
     *  to getResult().  While TaskHandler.executeTask() is invoked multiple times, TaskHandler.getResult() is normally
     *  invoked only a single time (once all tasks have been executed).
     */
    void executeTask(T item);
    S getResult();
}

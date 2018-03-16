package com.softwareverde.bitcoin.block.validator.thread;

public interface TaskHandlerFactory<T, S> {
    TaskHandler<T, S> newInstance();
}

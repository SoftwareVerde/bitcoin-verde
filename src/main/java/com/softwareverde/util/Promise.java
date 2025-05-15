package com.softwareverde.util;

public class Promise<T> {
    protected volatile boolean _isComplete = false;
    protected volatile T _result;
    protected volatile Exception _exception;

    public Promise() { }

    public Promise(final T completedValue) {
        _isComplete = true;
        _result = completedValue;
    }

    public synchronized void setResult(final T result) {
        _result = result;
        _isComplete = true;
        this.notifyAll();
    }

    public synchronized void waitForResult() throws InterruptedException {
        this.waitForResult(0L);
    }

    public synchronized void waitForResult(final long timeout) throws InterruptedException {
        if (! _isComplete) {
            this.wait(timeout);
        }
    }

    public synchronized T getResult() throws InterruptedException {
        return this.getResult(0L);
    }

    public synchronized T getResult(final long timeout) throws InterruptedException {
        if (! _isComplete) {
            this.wait(timeout);
        }
        return _result;
    }

    public T pollResult() {
        return _result;
    }

    public boolean isComplete() {
        return _isComplete;
    }

    public void setException(final Exception exception) {
        _exception = exception;
    }

    public boolean encounteredException() {
        return (_exception != null);
    }

    public Exception getException() {
        return _exception;
    }
}

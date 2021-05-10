package com.softwareverde.network.socket;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.logging.LoggerInstance;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.OutputStream;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BinarySocketWriteThread extends Thread implements Socket.WriteThread {
    protected static final LoggerInstance LOG = Logger.getInstance(BinarySocketWriteThread.class);

    protected final AtomicBoolean _isClosed = new AtomicBoolean(false);
    protected final AtomicLong _queuedMessageBufferByteCount = new AtomicLong(0L);
    protected final LinkedList<ByteArray> _queuedMessageBuffer = new LinkedList<>();

    protected final Integer _maxQueuedMessageBufferByteCount;
    protected final Integer _bufferByteCount;

    protected String _socketName = null;
    protected OutputStream _outputStream;
    protected Callback _callback;
    protected Long _totalBytesWritten = 0L;
    protected Long _totalBytesDroppedCount = 0L;
    protected Long _socketTimeoutMs = 30000L;

    // A timer of the current page being written, primarily used to detect a connection that is exceeding its TCP output buffer.
    //  If the timer is null then there is no current write happening.
    //  NanoTimer::stop must be called before invoke NanoTimer::getMillisecondsElapsed.
    protected NanoTimer _durationOfCurrentPageWrite = null;

    protected Long _getCurrentPageWriteDuration() {
        final NanoTimer nanoTimer = _durationOfCurrentPageWrite;
        if (nanoTimer == null) { return null; }

        nanoTimer.stop();
        final Double msElapsed = nanoTimer.getMillisecondsElapsed();
        return msElapsed.longValue();
    }

    protected void _close() {
        _isClosed.set(true);

        this.interrupt();

        final OutputStream outputStream = _outputStream;
        if (outputStream != null) {
            try {
                outputStream.close();
            }
            catch (final Exception exception) { }
        }
        _outputStream = null;
    }

    public BinarySocketWriteThread(final Integer bufferByteCount, final Integer maxQueuedMessageBufferByteCount) {
        this.setName("Binary Socket - Write Thread");
        _bufferByteCount = bufferByteCount;
        _maxQueuedMessageBufferByteCount = maxQueuedMessageBufferByteCount;
    }

    public void setSocketTimeout(final Long socketTimeoutMs) {
        _socketTimeoutMs = socketTimeoutMs;
    }

    @Override
    public void setSocketName(final String socketName) {
        this.setName("Binary Socket - Write Thread - " + socketName);
        _socketName = socketName;
    }

    @Override
    public void run() {
        final Thread thread = Thread.currentThread();
        try {
            final MutableByteArray buffer = new MutableByteArray(_bufferByteCount);
            final OutputStream outputStream = _outputStream;

            while ( (! thread.isInterrupted()) && (! _isClosed.get()) ) {
                final ByteArray byteArray;
                final int byteArrayByteCount;
                synchronized (_queuedMessageBuffer) {
                    byteArray = _queuedMessageBuffer.poll();

                    if (byteArray == null) {
                        _queuedMessageBuffer.wait();
                        continue;
                    }

                    byteArrayByteCount = byteArray.getByteCount();
                    _queuedMessageBufferByteCount.addAndGet(-byteArrayByteCount);
                }

                final boolean traceIsEnabled = LOG.isTraceEnabled();

                _durationOfCurrentPageWrite = new NanoTimer();
                int readIndex = 0;
                int byteCountRemaining = byteArrayByteCount;
                while ( (byteCountRemaining > 0) && (! thread.isInterrupted()) && (! _isClosed.get()) ) {
                    final int packetSize = Math.min(byteCountRemaining, buffer.getByteCount());

                    for (int i = 0; i < packetSize; ++i) {
                        buffer.setByte(i, byteArray.getByte(readIndex + i));
                    }

                    _durationOfCurrentPageWrite.start(); // The timer measures the time of the current page/packet, not the message as a whole...
                    outputStream.write(buffer.unwrap(), 0, packetSize);
                    _totalBytesWritten += packetSize;

                    if (traceIsEnabled) {
                        _durationOfCurrentPageWrite.stop();
                        LOG.trace("Sent " + packetSize + " bytes to socket " + _socketName + " in " + _durationOfCurrentPageWrite.getMillisecondsElapsed());
                    }

                    readIndex += packetSize;
                    byteCountRemaining -= packetSize;
                }
                outputStream.flush();
                _durationOfCurrentPageWrite = null;
            }
        }
        catch (final Exception exception) {
            LOG.debug(exception);
        }
        finally {
            _close();

            final Callback callback = _callback;
            if (callback != null) {
                callback.onExit();
            }
        }
    }

    @Override
    public synchronized void setOutputStream(final OutputStream outputStream) {
        final OutputStream rawOutputStream = _outputStream;
        if (rawOutputStream != null) {
            try {
                rawOutputStream.close();
            }
            catch (final Exception exception) { }
        }

        _outputStream = outputStream;
    }

    @Override
    public void setCallback(final Callback callback) {
        _callback = callback;
    }

    @Override
    public Boolean write(final ByteArray bytes) {
        if (_isClosed.get()) { return false; }

        final int byteCount = bytes.getByteCount();
        final long queuedByteCount = _queuedMessageBufferByteCount.get();
        final long newQueuedByteCount = (queuedByteCount + byteCount);

        final boolean itemWasAdded;
        if (newQueuedByteCount <= _maxQueuedMessageBufferByteCount) {
            synchronized (_queuedMessageBuffer) {
                itemWasAdded = _queuedMessageBuffer.add(bytes);

                if (itemWasAdded) {
                    _queuedMessageBufferByteCount.addAndGet(byteCount);
                    _queuedMessageBuffer.notifyAll();
                }
            }
        }
        else {
            itemWasAdded = false;
            _totalBytesDroppedCount += byteCount;

            LOG.debug("Socket queue full, dropping packet. (" + newQueuedByteCount + " > " + _maxQueuedMessageBufferByteCount + ") - " + _socketName);
        }

        final Long currentPageWriteDuration = Util.coalesce(_getCurrentPageWriteDuration());
        if (currentPageWriteDuration > _socketTimeoutMs) {
            LOG.debug("Socket timeout exceeded. (" + currentPageWriteDuration + "ms > " + _socketTimeoutMs + "ms - " + _socketName);
            synchronized (BinarySocketWriteThread.this) {
                _close();
            }
        }

        return itemWasAdded;
    }

    @Override
    public Long getTotalBytesWritten() {
        return _totalBytesWritten;
    }

    @Override
    public Long getTotalBytesDroppedCount() {
        return _totalBytesDroppedCount;
    }

    @Override
    public synchronized void close() {
        if (_isClosed.get()) { return; }
        _close();
    }
}
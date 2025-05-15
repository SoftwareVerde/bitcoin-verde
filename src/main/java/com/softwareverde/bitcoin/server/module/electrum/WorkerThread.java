package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Tuple;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class WorkerThread extends Thread {
    protected static final Long MAX_REQUEST_QUEUE_COUNT = 2500L;
    protected static final Long MAX_QUEUE_BYTE_COUNT = (64 * ByteUtil.Unit.Binary.MEBIBYTES);

    public interface RequestHandler {
        void handleRequest(Json json, JsonSocket jsonSocket);
        void handleError(Json json, JsonSocket jsonSocket);
    }

    protected final RequestHandler _requestHandler;
    protected final AtomicBoolean _isDead = new AtomicBoolean(false);
    protected final AtomicLong _pendingRequestCount = new AtomicLong(0L);
    protected final AtomicLong _pendingRequestByteCount = new AtomicLong(0L);
    protected final ConcurrentLinkedDeque<JsonSocket> _jsonSockets = new ConcurrentLinkedDeque<>();
    protected final ConcurrentLinkedDeque<Tuple<Json, JsonSocket>> _requestQueue = new ConcurrentLinkedDeque<>();

    protected void _die() {
        _isDead.set(true);
        for (final JsonSocket jsonSocket : _jsonSockets) {
            jsonSocket.close();
        }
        this.interrupt();

        _jsonSockets.clear();
        _requestQueue.clear();
    }

    public WorkerThread(final RequestHandler requestHandler) {
        _requestHandler = requestHandler;

        this.setDaemon(true);
        this.setName("Electrum Connection Worker Thread");
    }

    public void addSocket(final JsonSocket jsonSocket) {
        if (_isDead.get()) { return; }
        _jsonSockets.add(jsonSocket);
    }

    public void removeSocket(final JsonSocket jsonSocket) {
        _jsonSockets.remove(jsonSocket);
    }

    public Boolean hasActiveSockets() {
        for (final JsonSocket jsonSocket : _jsonSockets) {
            if (jsonSocket.isConnected()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void run() {
        while (! this.isInterrupted()) {
            synchronized (_requestQueue) {
                if (_requestQueue.isEmpty()) {
                    try {
                        _requestQueue.wait();
                    }
                    catch (final InterruptedException exception) { break; }
                }
            }

            while ( (! _requestQueue.isEmpty()) && (! this.isInterrupted()) ) {
                final Tuple<Json, JsonSocket> tuple = _requestQueue.removeFirst();
                final int byteCount = tuple.first.toString().length();
                _pendingRequestByteCount.getAndAdd(-byteCount);
                _pendingRequestCount.getAndDecrement();

                try {
                    _requestHandler.handleRequest(tuple.first, tuple.second);
                }
                catch (final Exception exception) {
                    Logger.debug(exception);

                    _requestHandler.handleError(tuple.first, tuple.second);
                }
            }
        }
    }

    public void enqueue(final Json json, final JsonSocket jsonSocket) {
        if (_isDead.get()) { return; }

        final int byteCount = json.toString().length();
        final long queueByteCount = _pendingRequestByteCount.addAndGet(byteCount);
        if (queueByteCount > WorkerThread.MAX_QUEUE_BYTE_COUNT) {
            _die();
            return;
        }

        synchronized (_requestQueue) {
            _requestQueue.add(new Tuple<>(json, jsonSocket));
            final long requestCount = _pendingRequestCount.incrementAndGet();

            if (requestCount > WorkerThread.MAX_REQUEST_QUEUE_COUNT) {
                _die();
                return;
            }

            _requestQueue.notifyAll();
        }
    }
}

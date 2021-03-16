package com.softwareverde.network.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class JsonSocket extends Socket {
    protected static class ReadThread extends Thread implements Socket.ReadThread {
        private InputStream _rawInputStream;
        private BufferedReader _bufferedReader;
        private Callback _callback;
        private Long _totalBytesReceived = 0L;

        @Override
        public void run() {
            final Thread thread = Thread.currentThread();
            try {
                while (! thread.isInterrupted()) {
                    final String string = _bufferedReader.readLine();

                    if (string == null) { break; }
                    if (string.isEmpty()) { continue; }

                    _totalBytesReceived += string.length(); // Not technically accurate.

                    if (Json.isJson(string)) {
                        final Json json = Json.parse(string);
                        final JsonProtocolMessage message = new JsonProtocolMessage(json);
                        if (_callback != null) {
                            _callback.onNewMessage(message);
                        }
                    }
                }
            }
            catch (final Exception exception) {
                Logger.trace("Exception occurred while reading line: " + exception);
            }
            finally {
                Logger.debug("Closing Json socket.");
                final Callback callback = _callback;
                if (callback != null) {
                    callback.onExit();
                }
            }
        }

        @Override
        public synchronized void setInputStream(final InputStream inputStream) {
            final InputStream rawInputStream = _rawInputStream;
            if (rawInputStream != null) {
                try {
                    rawInputStream.close();
                }
                catch (final Exception exception) { }
            }

            final BufferedReader bufferedReader = _bufferedReader;
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                }
                catch (final Exception exception) { }
            }

            if (inputStream != null) {
                _rawInputStream = inputStream;
                _bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            }
        }

        @Override
        public void setCallback(final Callback callback) {
            _callback = callback;
        }

        @Override
        public Long getTotalBytesReceived() {
            return _totalBytesReceived;
        }

        @Override
        public synchronized void close() {
            this.interrupt();

            // NOTE: the raw InputStream must be held and must be closed before the BufferedReader because the
            //  BufferedReader::close method is synchronized with the BufferedReader::readLine method, which would
            //  cause a deadlock 100% of the time.  Instead, the raw InputStream is closed, which causes the readLine
            //  function to except, so that the BufferedReader may be closed.
            final InputStream rawInputStream = _rawInputStream;
            if (rawInputStream != null) {
                try {
                    rawInputStream.close();
                }
                catch (final Exception exception) { }
            }

            final BufferedReader bufferedReader = _bufferedReader;
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                }
                catch (final Exception exception) { }
            }
            _bufferedReader = null;
        }
    }

    public JsonSocket(final java.net.Socket socket, final ThreadPool threadPool) {
        super(socket, new ReadThread(), threadPool);
    }

    @Override
    public JsonProtocolMessage popMessage() {
        return (JsonProtocolMessage) super.popMessage();
    }
}

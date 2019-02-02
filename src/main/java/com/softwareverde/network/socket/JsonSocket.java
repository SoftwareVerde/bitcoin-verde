package com.softwareverde.network.socket;

import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.json.Json;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class JsonSocket extends Socket {
    protected static class ReadThread extends Thread implements Socket.ReadThread {
        private BufferedReader _bufferedReader;
        private Callback _callback;

        @Override
        public void run() {
            while (true) {
                try {
                    final String string = _bufferedReader.readLine();
                    if (string == null) { break; }

                    if (Json.isJson(string)) {
                        final Json json = Json.parse(string);
                        final JsonProtocolMessage message = new JsonProtocolMessage(json);
                        if (_callback != null) {
                            _callback.onNewMessage(message);
                        }
                    }

                    if (this.isInterrupted()) { break; }
                }
                catch (final Exception exception) {
                    break;
                }
            }

            if (_callback != null) {
                _callback.onExit();
            }
        }

        @Override
        public void setInputStream(final InputStream inputStream) {
            if (_bufferedReader != null) {
                try {
                    _bufferedReader.close();
                }
                catch (final Exception exception) { }
            }

            _bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        }

        @Override
        public void setCallback(final Callback callback) {
            _callback = callback;
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

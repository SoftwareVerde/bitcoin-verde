package com.softwareverde.bitcoin.stratum;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;

import java.net.InetSocketAddress;
import java.net.Socket;

public class ProxyViaBtcMessageReceivedCallback implements Runnable {
    protected final ThreadPool _threadPool;
    protected final JsonSocket _jsonSocket;

    public ProxyViaBtcMessageReceivedCallback(final JsonSocket jsonSocket, final ThreadPool threadPool) {
        _threadPool = threadPool;
        _jsonSocket = jsonSocket;
    }

    @Override
    public void run() {
        try {
            final Socket socket = new Socket();
            socket.connect(new InetSocketAddress("bch.viabtc.com", 3333), 3000);
            if (! socket.isConnected()) { throw new RuntimeException("Unable to connect."); }

            final JsonSocket viaBtcSocket = new JsonSocket(socket, _threadPool);

            viaBtcSocket.setMessageReceivedCallback(new Runnable() {
                @Override
                public void run() {
                    final JsonProtocolMessage jsonProtocolMessage = viaBtcSocket.popMessage();
                    final Json message = jsonProtocolMessage.getMessage();
                    Logger.trace("ViaBtc SENT: " + message);

                    _jsonSocket.write(jsonProtocolMessage);
                }
            });

            _jsonSocket.setMessageReceivedCallback(new Runnable() {
                @Override
                public void run() {
                    final JsonProtocolMessage jsonProtocolMessage = _jsonSocket.popMessage();
                    final Json message = jsonProtocolMessage.getMessage();
                    Logger.trace("ASIC SENT: " + message);

                    viaBtcSocket.write(jsonProtocolMessage);
                }
            });

            viaBtcSocket.beginListening();
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }
    }
}

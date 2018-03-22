package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.stratum.client.message.SubscribeMessage;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.client.AuthorizeMessage;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.io.Logger;
import com.softwareverde.socket.SocketConnection;

import java.io.File;
import java.net.Socket;

public class StratumModule {

    protected final Configuration _configuration;
    protected final StratumServerSocket _stratumServerSocket;

    protected void _exitFailure() {
        System.exit(1);
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            _exitFailure();
        }

        return new Configuration(configurationFile);
    }

    public StratumModule(final String configurationFilename) {
        _configuration = _loadConfigurationFile(configurationFilename);

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
        final Configuration.DatabaseProperties databaseProperties = _configuration.getDatabaseProperties();

        try {
            Logger.log("Connecting to viabtc...");
            final SocketConnection socketConnection = new SocketConnection(new Socket("bch.viabtc.com", 3333));

            socketConnection.setMessageReceivedCallback(new Runnable() {
                @Override
                public void run() {
                    final String message = socketConnection.popMessage();

                    final RequestMessage requestMessage = RequestMessage.parse(message);
                    final ResponseMessage responseMessage = ResponseMessage.parse(message);

                    if (requestMessage != null) { System.out.println(requestMessage); }
                    if (responseMessage != null) { System.out.println(responseMessage); }
                }
            });

            { // Subscribe for mining updates...
                final SubscribeMessage subscribeMessage = new SubscribeMessage();
                socketConnection.write(subscribeMessage.toString());
            }

            { // Authorize to viabtc...
                final AuthorizeMessage authorizeMessage = new AuthorizeMessage();
                authorizeMessage.setCredentials("username", "password");
                socketConnection.write(authorizeMessage.toString());
            }

        } catch (final Exception e) { e.printStackTrace(); }

        _stratumServerSocket = new StratumServerSocket(serverProperties.getStratumPort());
        _stratumServerSocket.setSocketEventCallback(new StratumServerSocket.SocketEventCallback() {
            @Override
            public void onConnect(final SocketConnection socketConnection) {
                socketConnection.setMessageReceivedCallback(new Runnable() {
                    @Override
                    public void run() {
                        final String message = socketConnection.popMessage();

                        final RequestMessage requestMessage = RequestMessage.parse(message);
                        final ResponseMessage responseMessage = ResponseMessage.parse(message);

                        if (requestMessage != null) { System.out.println(requestMessage); }
                        if (responseMessage != null) { System.out.println(responseMessage); }
                    }
                });
            }

            @Override
            public void onDisconnect(final SocketConnection socketConnection) { }
        });
    }

    public void loop() {

        _stratumServerSocket.start();

        Logger.log("[Server Online]");

        while (true) {
            try { Thread.sleep(5000); } catch (final Exception e) { }
        }
    }

    public static void execute(final String configurationFileName) {
        final StratumModule stratumModule = new StratumModule(configurationFileName);
        stratumModule.loop();
    }
}
package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.stratum.client.message.SubscribeMessage;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage.ClientCommand;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.client.AuthorizeMessage;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.socket.SocketConnection;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;

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

    protected ByteArray _createRandomBytes(final int byteCount) {
        int i=0;
        final MutableByteArray mutableByteArray = new MutableByteArray(byteCount);
        while (i < byteCount) {
            final byte[] randomBytes = ByteUtil.integerToBytes((int) (Math.random() * Integer.MAX_VALUE));
            for (byte b : randomBytes) {
                mutableByteArray.set(i, b);
                i += 1;
                if (i >= byteCount) { break; }
            }
        }
        return mutableByteArray;
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
                Logger.log("Node connected.");

                socketConnection.setMessageReceivedCallback(new Runnable() {
                    @Override
                    public void run() {
                        final String message = socketConnection.popMessage();
                        System.out.println("Raw Message: "+ message);

                        { // Handle Request Messages...
                            final RequestMessage requestMessage = RequestMessage.parse(message);
                            if (requestMessage != null) {
                                Logger.log("Received: "+ requestMessage);

                                if (requestMessage.isCommand(ClientCommand.SUBSCRIBE)) {
                                    final ByteArray subscriptionId = _createRandomBytes(8);
                                    final ByteArray extraNonce1 = _createRandomBytes(4); // ExtraNonce1 + ExtraNonce2ByteCount should equal 8 bytes.
                                    final Integer extraNonce2ByteCount = 4;

                                    final Json resultJson = new Json(true); {
                                        final Json setDifficulty = new Json(true); {
                                            setDifficulty.add("mining.set_difficulty");
                                            setDifficulty.add(subscriptionId);
                                        }

                                        final Json notify = new Json(true); {
                                            notify.add("mining.notify");
                                            notify.add(subscriptionId);
                                        }

                                        final Json subscriptions = new Json(true); {
                                            subscriptions.add(setDifficulty);
                                            subscriptions.add(notify);
                                        }

                                        resultJson.add(subscriptions);
                                        resultJson.add(extraNonce1);
                                        resultJson.add(extraNonce2ByteCount);
                                    }

                                    final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
                                    responseMessage.setResult(resultJson);
                                    socketConnection.write(responseMessage.toString());
                                }
                                else if (requestMessage.isCommand(ClientCommand.AUTHORIZE)) {
                                    {
                                        final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
                                        responseMessage.setResult(ResponseMessage.RESULT_TRUE);
                                        socketConnection.write(responseMessage.toString());
                                    }

                                    {
                                        // {"method":"mining.notify","id":0,"params": [
                                        //  jobId:                      "1FE8",
                                        //  previousBlockHash:          "CB81CDB5ED9DFDCC7973B4D9A0280CAFA6FF6698011AA2F60000000000000000",
                                        //  coinbaseTransactionPart1:   "01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF5D0368F9071A2F5669614254432F4D696E656420627920757365726E616D652F2CFABE6D6D52D42620289BA8F85B89CAA13794AFBD611B2DB2746B4340070B6C6135E81B00010000000000000010E81F800C93E4D840",
                                        //  coinbaseTransactionPart2:   "FFFFFFFF01C8CE864A000000001976A914F1C075A01882AE0972F95D3A4177C86C852B7D9188AC00000000",
                                        //  merkleTree:                 ["5C62C5F5C0C5A456C489203E3CA0427991BD4647A0334142E0E5F78BC987682B","2077E1D8CB57A4C6EC8DDF6D6E8470A04FF7F98030BE721A5B83B21ED939E76D","F051F497B0A178ED509A7D3335CD0528A0B28ACFD5094D07EBBB0629B606D759","8413CBE0D9903F5E5499359BD85A9DB986DA614CE4C6A228C53969573FC8A748","07381797C454A3D76DF6D7E6E601A9682C26B341F7E8F9E3E8E1549784074A33","FE0F47DCD89325926FA507045F0CDB2C9A124719B7FC15EA93DB120BC7C29592","AF6EEE880810751C48B94118FBD7DEE1904B673584BC6C26A9C36134E42C968A"],
                                        //  blockVersion:               "20000000",
                                        //  difficulty:                 "1802D306",
                                        //  timestamp:                  "5AB454FB",
                                        //  stopOtherJobs:              true
                                        // ]}

                                        // Job ID. This is included when miners submit a results so work can be matched with proper transactions.
                                        // Hash of previous block. Used to build the header.
                                        // Generation transaction (part 1). The miner inserts ExtraNonce1 and ExtraNonce2 after this section of the transaction data.
                                        // Generation transaction (part 2). The miner appends this after the first part of the transaction data and the two ExtraNonce values.
                                        // List of merkle branches. The generation transaction is hashed against the merkle branches to build the final merkle root.
                                        // Bitcoin block version. Used in the block header.
                                        // nBits. The encoded network difficulty. Used in the block header.
                                        // nTime. The current time. nTime rolling should be supported, but should not increase faster than actual time.
                                        // Clean Jobs. If true, miners should abort their current work and immediately use the new job. If false, they can still use the current job, but should move to the new one after exhausting the current nonce range.

                                        final RequestMessage mineBlockMessage = new RequestMessage(RequestMessage.ServerCommand.NOTIFY.getValue());
                                        {
                                            final ByteArray taskId = MutableByteArray.wrap(HexUtil.hexStringToByteArray("0000"));
                                            final Hash previousBlockHash = new ImmutableHash();
                                            final Json parametersJson = new Json(true);

                                            parametersJson.add(taskId);
                                            parametersJson.add(previousBlockHash);


                                            mineBlockMessage.setParameters(parametersJson);
                                        }
                                        socketConnection.write(mineBlockMessage.toString());
                                    }
                                }
                            }
                        }

                        { // Handle Response Messages...
                            final ResponseMessage responseMessage = ResponseMessage.parse(message);
                            if (responseMessage != null) { System.out.println(responseMessage); }
                        }
                    }
                });
            }

            @Override
            public void onDisconnect(final SocketConnection socketConnection) {
                Logger.log("Node disconnected.");
            }
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
package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.stratum.StratumMineBlockTask;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerSubmitBlockResult;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.socket.SocketConnection;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;

import java.io.File;

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

        final StratumMineBlockTask stratumMineBlockTask = new StratumMineBlockTask();

        final PrivateKey privateKey = PrivateKey.createNewKey();
        final Integer extraNonceByteCount = 4;
        final Integer extraNonce2ByteCount = 4;

        final ByteArray extraNonce = _createRandomBytes(extraNonceByteCount);

        stratumMineBlockTask.setExtraNonce(extraNonce);

        Logger.log("Private Key: " + privateKey);
        Logger.log("Address:     " + Address.fromPrivateKey(privateKey).toBase58CheckEncoded());

        _stratumServerSocket = new StratumServerSocket(serverProperties.getStratumPort());

        _stratumServerSocket.setSocketEventCallback(new StratumServerSocket.SocketEventCallback() {

            @Override
            public void onConnect(final SocketConnection socketConnection) {
                Logger.log("Node connected.");

                socketConnection.setMessageReceivedCallback(new Runnable() {
                    @Override
                    public void run() {
                        final String message = socketConnection.popMessage();

                        { // Handle Request Messages...
                            final RequestMessage requestMessage = RequestMessage.parse(message);
                            if (requestMessage != null) {
                                Logger.log("Received: "+ requestMessage);

                                if (requestMessage.isCommand(RequestMessage.ClientCommand.SUBSCRIBE)) {
                                    final String subscriptionId = HexUtil.toHexString(_createRandomBytes(8).getBytes());

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
                                        resultJson.add(extraNonce);
                                        resultJson.add(extraNonce2ByteCount);
                                    }

                                    final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
                                    responseMessage.setResult(resultJson);

                                    Logger.log("Sent: "+ responseMessage.toString());
                                    socketConnection.write(responseMessage.toString());
                                }
                                else if (requestMessage.isCommand(RequestMessage.ClientCommand.AUTHORIZE)) {
                                    {
                                        final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
                                        responseMessage.setResult(ResponseMessage.RESULT_TRUE);

                                        Logger.log("Sent: "+ responseMessage.toString());
                                        socketConnection.write(responseMessage.toString());
                                    }

                                    { // Submit work request...
                                        stratumMineBlockTask.setBlockVersion(BlockHeader.VERSION);
                                        stratumMineBlockTask.setPreviousBlockHash(BlockHeader.GENESIS_BLOCK_HEADER_HASH);
                                        stratumMineBlockTask.setDifficulty(Difficulty.BASE_DIFFICULTY);

                                        final Integer totalExtraNonceByteCount = (extraNonceByteCount + extraNonce2ByteCount);

                                        final Transaction coinbaseTransaction;
                                        {
                                            final String coinbaseMessage = "/Mined via " + Constants.USER_AGENT + "/";
                                            final Address address = Address.fromPrivateKey(privateKey);
                                            final Long satoshis = (50 * Transaction.SATOSHIS_PER_BITCOIN);
                                            coinbaseTransaction = Transaction.createCoinbaseTransactionWithExtraNonce(coinbaseMessage, totalExtraNonceByteCount, address, satoshis);
                                        }
                                        stratumMineBlockTask.setCoinbaseTransaction(coinbaseTransaction, totalExtraNonceByteCount);

                                        final RequestMessage mineBlockRequest = stratumMineBlockTask.createRequest();

                                        Logger.log("Sent: "+ mineBlockRequest.toString());
                                        socketConnection.write(mineBlockRequest.toString());
                                    }
                                }
                                else if (requestMessage.isCommand(RequestMessage.ClientCommand.SUBMIT)) {
                                    // mining.submit("username", "job id", "ExtraNonce2", "nTime", "nOnce")

                                    final Json messageParameters = requestMessage.getParameters();
                                    final String workerName = messageParameters.getString(0);
                                    final ByteArray taskId = MutableByteArray.wrap(HexUtil.hexStringToByteArray(messageParameters.getString(1)));
                                    final String stratumNonce = messageParameters.getString(4);
                                    final String stratumExtraNonce2 = messageParameters.getString(2);
                                    final String stratumTimestamp = messageParameters.getString(3);

                                    final BlockHeader blockHeader = stratumMineBlockTask.assembleBlockHeader(stratumNonce, stratumExtraNonce2, stratumTimestamp);
                                    final Hash hash = blockHeader.getHash();
                                    Logger.log(hash);

                                    final ResponseMessage blockAcceptedMessage = new MinerSubmitBlockResult(requestMessage.getId(), blockHeader.isValid());

                                    if (blockHeader.isValid()) {
                                        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
                                        Logger.log("Valid Block: "+ HexUtil.toHexString(blockHeaderDeflater.toBytes(blockHeader)));

                                        final BlockDeflater blockDeflater = new BlockDeflater();
                                        final Block block = stratumMineBlockTask.assembleBlock(stratumNonce, stratumExtraNonce2, stratumTimestamp);
                                        Logger.log(blockDeflater.toBytes(block));

                                        socketConnection.close();
                                        _stratumServerSocket.stop();
                                    }

                                    Logger.log("Sent: "+ blockAcceptedMessage.toString());
                                    socketConnection.write(blockAcceptedMessage.toString());
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
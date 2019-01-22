package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.stratum.StratumMineBlockTask;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerSubmitBlockResult;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.socket.SocketConnection;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.StringUtil;

import java.io.File;

public class StratumModule {

    protected final Configuration _configuration;
    protected final StratumServerSocket _stratumServerSocket;
    protected final MainThreadPool _threadPool = new MainThreadPool(256, 60000L);

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            BitcoinUtil.exitFailure();
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

        // final PrivateKey privateKey = PrivateKey.createNewKey();
        final Integer extraNonceByteCount = 4;
        final Integer extraNonce2ByteCount = 4;

        final ByteArray extraNonce = _createRandomBytes(extraNonceByteCount);

        stratumMineBlockTask.setExtraNonce(extraNonce);

        {
            final String blockData = "010000004B0360D834A330EC7833E30E1F523EE05A0793361E29A73421964F980000000027B64A020AF294E903FEED93768705336A20090612A043F47AF462A2F5E5B564F8EE3A4B6AD8001DD3A437070101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF07046AD8001D0104FFFFFFFF0100F2052A0100000043410428F88CA471C9718C4E52DF12B756BABEDF6A970082C3CC2BDC9F7E0C53479B7F0D9201FD4B0C3EB3E82C48EF6C011B51994EBC18177C85B20FFE8FC844ECA755AC00000000";
            final BlockInflater blockInflater = new BlockInflater();
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

            stratumMineBlockTask.setBlockVersion(block.getVersion());
            stratumMineBlockTask.setPreviousBlockHash(Sha256Hash.fromHexString("F6B44766062D3179A7806C551A662B971CBE531B86CE00B1E0320BA9725C07D7"));
            stratumMineBlockTask.setDifficulty(block.getDifficulty());

            final Transaction coinbaseTransaction;
            {
                final Long blockHeight = 1L;
                final MutableTransaction mutableTransaction = new MutableTransaction(block.getCoinbaseTransaction());
                final TransactionInput transactionInput = mutableTransaction.getTransactionInputs().get(0);
                final ByteArray unlockingScriptBytes = transactionInput.getUnlockingScript().getBytes();
                final TransactionInput newTransactionInput = TransactionInput.createCoinbaseTransactionInputWithExtraNonce(blockHeight, StringUtil.bytesToString(unlockingScriptBytes.getBytes()), extraNonceByteCount);
                mutableTransaction.setTransactionInput(0, newTransactionInput);
                coinbaseTransaction = mutableTransaction;
            }
            final Integer totalExtraNonceByteCount = (extraNonceByteCount + extraNonce2ByteCount);
            stratumMineBlockTask.setCoinbaseTransaction(coinbaseTransaction, totalExtraNonceByteCount);
        }

        // Logger.log("Private Key: " + privateKey);
        // Logger.log("Address:     " + Address.fromPrivateKey(privateKey).toBase58CheckEncoded());

        _stratumServerSocket = new StratumServerSocket(serverProperties.getStratumPort(), _threadPool);

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
                                    final Sha256Hash hash = blockHeader.getHash();
                                    Logger.log(hash);

                                    final ResponseMessage blockAcceptedMessage = new MinerSubmitBlockResult(requestMessage.getId(), blockHeader.isValid());

                                    if (blockHeader.isValid()) {
                                        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
                                        Logger.log("Valid Block: "+ blockHeaderDeflater.toBytes(blockHeader));

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
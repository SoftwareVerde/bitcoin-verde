package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.server.configuration.Configuration;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerSubmitBlockResult;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.server.stratum.task.LiveStratumMineBlockTaskBuilderCore;
import com.softwareverde.bitcoin.server.stratum.task.StratumMineBlockTask;
import com.softwareverde.bitcoin.server.stratum.task.StratumMineBlockTaskBuilderCore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;

import java.io.File;

/**
 * Generates TestBlock Data via Stratum.
 *  NOTE: Stratum Mining always modifies the coinbase's ExtraNonce, which may be undesirable.
 */
public class TestBlockDataStratumMiner {
    // @Test
    public void run() {
        final String coinbaseMessage = BitcoinConstants.getCoinbaseMessage();

        final TransactionInflater transactionInflater = new TransactionInflater();
        final AddressInflater addressInflater = new AddressInflater();

        final PrivateKey coinbasePrivateKey = PrivateKey.createNewKey();
        Logger.info("Private Key: " + coinbasePrivateKey);
        Logger.info("Address:     " + addressInflater.fromPrivateKey(coinbasePrivateKey, false).toBase58CheckEncoded());

        final Address address = addressInflater.fromPrivateKey(coinbasePrivateKey, false);

        final Long blockHeight = 1L; // ???
        final Sha256Hash previousBlockHash = BlockHeader.GENESIS_BLOCK_HASH; // ???

        final Difficulty difficulty = Difficulty.BASE_DIFFICULTY;

        final Transaction coinbaseTransaction = transactionInflater.createCoinbaseTransactionWithExtraNonce(blockHeight, coinbaseMessage, StratumMiner.totalExtraNonceByteCount, address, BlockHeader.calculateBlockReward(blockHeight));

        final MutableList<Transaction> transactions = new MutableList<Transaction>();

//        {
//            final PrivateKey prevoutPrivateKey = ???;
//
//            final MutableTransaction transaction = new MutableTransaction();
//            transaction.setVersion(Transaction.VERSION);
//            transaction.setLockTime(LockTime.MIN_TIMESTAMP);
//            final TransactionOutput outputBeingSpent;
//            {
//                final Transaction transactionToSpend = ???;
//                outputBeingSpent = ???;
//
//                final MutableTransactionInput transactionInput = new MutableTransactionInput();
//                transactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
//                transactionInput.setPreviousOutputIndex(0);
//                transactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
//                transactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
//                transaction.addTransactionInput(transactionInput);
//            }
//
//            final MutableTransactionOutput newTransactionOutput;
//            {
//                final PrivateKey newTransactionOutputPrivateKey = PrivateKey.createNewKey();
//                final Address payToAddress = addressInflater.fromPrivateKey(newTransactionOutputPrivateKey);
//                System.out.println("Tx Private Key: " + newTransactionOutputPrivateKey);
//                newTransactionOutput = new MutableTransactionOutput();
//                newTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
//                newTransactionOutput.setIndex(0);
//                newTransactionOutput.setLockingScript(ScriptBuilder.payToAddress(payToAddress));
//            }
//            transaction.addTransactionOutput(newTransactionOutput);
//
//            final SignatureContext signatureContext = new SignatureContext(transaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, false), Long.MAX_VALUE);
//            signatureContext.setShouldSignInputScript(0, true, outputBeingSpent);
//            final TransactionSigner transactionSigner = new TransactionSigner();
//            final Transaction signedTransaction = transactionSigner.signTransaction(signatureContext, prevoutPrivateKey);
//
//            final TransactionInput transactionInput = signedTransaction.getTransactionInputs().get(0);
//            final MutableContext context = new MutableContext();
//            context.setCurrentScript(null);
//            context.setTransactionInputIndex(0);
//            context.setTransactionInput(transactionInput);
//            context.setTransaction(signedTransaction);
//            context.setBlockHeight(blockHeight);
//            context.setTransactionOutputBeingSpent(outputBeingSpent);
//            context.setCurrentScriptLastCodeSeparatorIndex(0);
//            final ScriptRunner scriptRunner = new ScriptRunner();
//            final Boolean outputIsUnlocked = scriptRunner.runScript(outputBeingSpent.getLockingScript(), transactionInput.getUnlockingScript(), context);
//            Assert.assertTrue(outputIsUnlocked);
//
//            transactions.add(signedTransaction);
//        }

        final StratumMiner.BlockConfiguration blockConfiguration = new StratumMiner.BlockConfiguration();
        blockConfiguration.blockVersion = BlockHeader.VERSION;
        blockConfiguration.coinbaseTransaction = coinbaseTransaction;
        blockConfiguration.difficulty = difficulty;
        blockConfiguration.previousBlockHash = previousBlockHash;
        blockConfiguration.transactions = transactions;

        final StratumMiner stratumMiner = new StratumMiner(blockConfiguration);
        stratumMiner.loop();
    }
}

class StratumMiner {
    public static final Integer extraNonceByteCount = 4;
    public static final Integer extraNonce2ByteCount = 4;
    public static final Integer totalExtraNonceByteCount = (extraNonceByteCount + extraNonce2ByteCount);

    protected final StratumServerSocket _stratumServerSocket;
    protected final CachedThreadPool _threadPool = new CachedThreadPool(256, 60000L);

    protected final ByteArray _extraNonce;

    protected StratumMineBlockTaskBuilderCore _stratumMineBlockTaskBuilder;
    protected StratumMineBlockTask _currentMineBlockTask = null;

    protected Integer _shareDifficulty = 1;

    protected Thread _mainThread;


    public static class BlockConfiguration {
        Long blockVersion;
        Sha256Hash previousBlockHash;
        Difficulty difficulty;
        Transaction coinbaseTransaction;
        List<Transaction> transactions;
    }

    protected final BlockConfiguration _blockConfiguration;

    protected ByteArray _createRandomBytes(final int byteCount) {
        int i = 0;
        final MutableByteArray mutableByteArray = new MutableByteArray(byteCount);
        while (i < byteCount) {
            final byte[] randomBytes = ByteUtil.integerToBytes((int) (Math.random() * Integer.MAX_VALUE));
            for (byte b : randomBytes) {
                mutableByteArray.setByte(i, b);
                i += 1;
                if (i >= byteCount) { break; }
            }
        }
        return mutableByteArray;
    }

    protected void _buildMiningTask() {
        final LiveStratumMineBlockTaskBuilderCore stratumMineBlockTaskBuilder = new LiveStratumMineBlockTaskBuilderCore(totalExtraNonceByteCount, new TransactionDeflater());

        stratumMineBlockTaskBuilder.setBlockVersion(_blockConfiguration.blockVersion);
        stratumMineBlockTaskBuilder.setPreviousBlockHash(_blockConfiguration.previousBlockHash);
        stratumMineBlockTaskBuilder.setDifficulty(_blockConfiguration.difficulty);
        stratumMineBlockTaskBuilder.setCoinbaseTransaction(_blockConfiguration.coinbaseTransaction);
        stratumMineBlockTaskBuilder.setExtraNonce(_extraNonce);

        for (final Transaction transaction : _blockConfiguration.transactions) {
            stratumMineBlockTaskBuilder.addTransaction(new TransactionWithFee(transaction, 0L));
        }

        _stratumMineBlockTaskBuilder = stratumMineBlockTaskBuilder;
        _currentMineBlockTask = stratumMineBlockTaskBuilder.buildMineBlockTask();
    }

    protected void _sendWork(final JsonSocket socketConnection, final Boolean abandonOldJobs) {
        _setDifficulty(socketConnection);

        final RequestMessage mineBlockRequest = _currentMineBlockTask.createRequest(abandonOldJobs);

        Logger.info("Sent: "+ mineBlockRequest.toString());
        socketConnection.write(new JsonProtocolMessage(mineBlockRequest));
    }

    protected void _setDifficulty(final JsonSocket socketConnection) {
        final RequestMessage mineBlockMessage = new RequestMessage(RequestMessage.ServerCommand.SET_DIFFICULTY.getValue());

        final Json parametersJson = new Json(true);
        parametersJson.add(_shareDifficulty); // Difficulty::getDifficultyRatio
        mineBlockMessage.setParameters(parametersJson);

        Logger.info("Sent: "+ mineBlockMessage.toString());
        socketConnection.write(new JsonProtocolMessage(mineBlockMessage));
    }

    protected void _handleSubscribeMessage(final RequestMessage requestMessage, final JsonSocket socketConnection) {
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
            resultJson.add(_currentMineBlockTask.getExtraNonce());
            resultJson.add(extraNonce2ByteCount);
        }

        final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
        responseMessage.setResult(resultJson);

        Logger.info("Sent: "+ responseMessage);
        socketConnection.write(new JsonProtocolMessage(responseMessage));
    }

    protected void _handleAuthorizeMessage(final RequestMessage requestMessage, final JsonSocket socketConnection) {
        { // Respond with successful authorization...
            final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
            responseMessage.setResult(ResponseMessage.RESULT_TRUE);

            Logger.info("Sent: "+ responseMessage.toString());
            socketConnection.write(new JsonProtocolMessage(responseMessage));
        }

        _sendWork(socketConnection, true);
    }

    protected void _handleSubmitMessage(final RequestMessage requestMessage, final JsonSocket socketConnection) {
        // mining.submit("username", "job id", "ExtraNonce2", "nTime", "nOnce")

        final Json messageParameters = requestMessage.getParameters();
        final String workerName = messageParameters.getString(0);
        final ByteArray taskId = MutableByteArray.wrap(HexUtil.hexStringToByteArray(messageParameters.getString(1)));
        final String stratumNonce = messageParameters.getString(4);
        final String stratumExtraNonce2 = messageParameters.getString(2);
        final String stratumTimestamp = messageParameters.getString(3);

        boolean submissionWasAccepted = true;

        final Long taskIdLong = ByteUtil.bytesToLong(taskId.getBytes());
        final StratumMineBlockTask mineBlockTask = _currentMineBlockTask;
        if (mineBlockTask == null) {
            submissionWasAccepted = false;
        }

        boolean shouldExit = false;

        if (mineBlockTask != null) {
            final Difficulty shareDifficulty = Difficulty.BASE_DIFFICULTY.divideBy(_shareDifficulty);

            final BlockHeader blockHeader = mineBlockTask.assembleBlockHeader(stratumNonce, stratumExtraNonce2, stratumTimestamp);
            final Sha256Hash hash = blockHeader.getHash();
            Logger.info(mineBlockTask.getDifficulty().getBytes());
            Logger.info(hash);
            Logger.info(shareDifficulty.getBytes());

            if (! shareDifficulty.isSatisfiedBy(hash)) {
                submissionWasAccepted = false;
                Logger.info("NOTICE: Share Difficulty not satisfied.");

                final RequestMessage newRequestMessage = mineBlockTask.createRequest(false);
                Logger.info("Resending Task: "+ newRequestMessage.toString());
                socketConnection.write(new JsonProtocolMessage(newRequestMessage));
            }
            else if (blockHeader.isValid()) {
                final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
                Logger.info("Valid Block: " + blockHeaderDeflater.toBytes(blockHeader));

                final BlockDeflater blockDeflater = new BlockDeflater();
                final Block block = mineBlockTask.assembleBlock(stratumNonce, stratumExtraNonce2, stratumTimestamp);
                Logger.info(blockDeflater.toBytes(block));

                shouldExit = true;
                BitcoinUtil.exitSuccess();
            }
        }

        final ResponseMessage blockAcceptedMessage = new MinerSubmitBlockResult(requestMessage.getId(), submissionWasAccepted);

        Logger.info("Sent: "+ blockAcceptedMessage.toString());
        socketConnection.write(new JsonProtocolMessage(blockAcceptedMessage));

        if (shouldExit) {
            socketConnection.close();
            _mainThread.interrupt();
        }
    }

    public StratumMiner(final BlockConfiguration blockConfiguration) {
        _extraNonce = _createRandomBytes(extraNonceByteCount);
        _blockConfiguration = blockConfiguration;

        File TEMP_FILE = null;
        try {
            TEMP_FILE = File.createTempFile("tmp", ".dat");
            TEMP_FILE.deleteOnExit();
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }

        final Configuration configuration = new Configuration(TEMP_FILE);
        final StratumProperties stratumProperties = configuration.getStratumProperties();

        _stratumServerSocket = new StratumServerSocket(stratumProperties.getPort(), _threadPool);

        _stratumServerSocket.setSocketEventCallback(new StratumServerSocket.SocketEventCallback() {
            @Override
            public void onConnect(final JsonSocket socketConnection) {
                Logger.info("Node connected: " + socketConnection.getIp() + ":" + socketConnection.getPort());

                socketConnection.setMessageReceivedCallback(new Runnable() {
                    @Override
                    public void run() {
                        final JsonProtocolMessage jsonProtocolMessage = socketConnection.popMessage();
                        final Json message = jsonProtocolMessage.getMessage();

                        { // Handle Request Messages...
                            final RequestMessage requestMessage = RequestMessage.parse(message);
                            if (requestMessage != null) {
                                Logger.info("Received: " + requestMessage);

                                if (requestMessage.isCommand(RequestMessage.ClientCommand.SUBSCRIBE)) {
                                    _handleSubscribeMessage(requestMessage, socketConnection);
                                }
                                else if (requestMessage.isCommand(RequestMessage.ClientCommand.AUTHORIZE)) {
                                    _handleAuthorizeMessage(requestMessage, socketConnection);
                                }
                                else if (requestMessage.isCommand(RequestMessage.ClientCommand.SUBMIT)) {
                                    _handleSubmitMessage(requestMessage, socketConnection);
                                }
                                else {
                                    Logger.info("Unrecognized Message: " + requestMessage.getCommand());
                                }
                            }
                        }

                        { // Handle Response Messages...
                            final ResponseMessage responseMessage = ResponseMessage.parse(message);
                            if (responseMessage != null) { System.out.println(responseMessage); }
                        }
                    }
                });

                socketConnection.beginListening();
            }

            @Override
            public void onDisconnect(final JsonSocket disconnectedSocket) {
                Logger.info("Node disconnected: " + disconnectedSocket.getIp() + ":" + disconnectedSocket.getPort());
            }
        });
    }

    public void loop() {
        _mainThread = Thread.currentThread();
        _threadPool.start();

        _buildMiningTask();

        _stratumServerSocket.start();

        Logger.info("[Server Online]");

        while (true) {
            try { Thread.sleep(60000); } catch (final Exception exception) { break; }
        }

        _stratumServerSocket.stop();
        _threadPool.stop();
    }
}
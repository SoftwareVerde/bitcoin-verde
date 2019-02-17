package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerSubmitBlockResult;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.server.stratum.task.StratumMineBlockTask;
import com.softwareverde.bitcoin.server.stratum.task.StratumMineBlockTaskFactory;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class TestBlockDataStratumMiner {
    // @Test
    public void run() {
        final StratumMiner stratumMiner = new StratumMiner();
        stratumMiner.loop();
    }
}

class StratumMiner {
    protected final StratumServerSocket _stratumServerSocket;
    protected final MainThreadPool _threadPool = new MainThreadPool(256, 60000L);

    protected final Integer _extraNonceByteCount = 4;
    protected final Integer _extraNonce2ByteCount = 4;
    protected final Integer _totalExtraNonceByteCount = (_extraNonceByteCount + _extraNonce2ByteCount);
    protected final ByteArray _extraNonce;

    protected StratumMineBlockTaskFactory _stratumMineBlockTaskFactory;
    protected StratumMineBlockTask _currentMineBlockTask = null;

    protected Integer _shareDifficulty = 1;

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected ByteArray _createRandomBytes(final int byteCount) {
        int i = 0;
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

    protected void _buildMiningTask() {
        final StratumMineBlockTaskFactory stratumMineBlockTaskFactory = new StratumMineBlockTaskFactory();

        final String coinbaseMessage = Constants.COINBASE_MESSAGE;

        final AddressInflater addressInflater = new AddressInflater();

        final PrivateKey coinbasePrivateKey = PrivateKey.createNewKey();
        Logger.log("Private Key: " + coinbasePrivateKey);
        Logger.log("Address:     " + addressInflater.fromPrivateKey(coinbasePrivateKey).toBase58CheckEncoded());

        final Address address = addressInflater.fromPrivateKey(coinbasePrivateKey);

        final Long blockHeight = 2L;
        final Sha256Hash previousBlockHash = Sha256Hash.fromHexString("0000000001BE52D653305F7D80ED373837E61CC26AE586AFD343A3C2E64E64A2");
        final Difficulty difficulty = Difficulty.BASE_DIFFICULTY;

        final Transaction coinbaseTransaction = Transaction.createCoinbaseTransactionWithExtraNonce(blockHeight, coinbaseMessage, _totalExtraNonceByteCount, address, BlockHeader.calculateBlockReward(blockHeight));

        final MutableList<Transaction> transactions = new MutableList<Transaction>();

        {
            final PrivateKey prevoutPrivateKey = PrivateKey.fromHexString("BD28A99DD044F142B8DC78101999671A9C5C15F617347EFC046F64CCF67AF67F");

            final MutableTransaction transaction = new MutableTransaction();
            transaction.setVersion(Transaction.VERSION);
            transaction.setLockTime(LockTime.MIN_TIMESTAMP);
            final TransactionOutput outputBeingSpent;
            {
                final Transaction transactionToSpend = coinbaseTransaction;
                // outputBeingSpent = transactionToSpend.getTransactionOutputs().get(0);
                final BlockInflater blockInflater = new BlockInflater();
                final Block previousBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain5.BLOCK_1));
                outputBeingSpent = previousBlock.getTransactions().get(0).getTransactionOutputs().get(0);

                final MutableTransactionInput transactionInput = new MutableTransactionInput();
                transactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
                transactionInput.setPreviousOutputIndex(0);
                transactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                transactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                transaction.addTransactionInput(transactionInput);
            }

            final MutableTransactionOutput newTransactionOutput;
            {
                final PrivateKey privateKey2 = PrivateKey.createNewKey();
                final Address payToAddress = addressInflater.fromPrivateKey(privateKey2);
                System.out.println("Private Key 2: " + privateKey2);
                newTransactionOutput = new MutableTransactionOutput();
                newTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
                newTransactionOutput.setIndex(0);
                newTransactionOutput.setLockingScript(ScriptBuilder.payToAddress(payToAddress));
            }
            transaction.addTransactionOutput(newTransactionOutput);

            final SignatureContext signatureContext = new SignatureContext(transaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, false), Long.MAX_VALUE);
            signatureContext.setShouldSignInputScript(0, true, outputBeingSpent);
            final TransactionSigner transactionSigner = new TransactionSigner();
            final Transaction signedTransaction = transactionSigner.signTransaction(signatureContext, prevoutPrivateKey);

            final TransactionInput transactionInput = signedTransaction.getTransactionInputs().get(0);
            final MutableContext context = new MutableContext();
            context.setCurrentScript(null);
            context.setTransactionInputIndex(0);
            context.setTransactionInput(transactionInput);
            context.setTransaction(signedTransaction);
            context.setBlockHeight(blockHeight);
            context.setTransactionOutputBeingSpent(outputBeingSpent);
            context.setCurrentScriptLastCodeSeparatorIndex(0);
            final ScriptRunner scriptRunner = new ScriptRunner();
            final Boolean outputIsUnlocked = scriptRunner.runScript(outputBeingSpent.getLockingScript(), transactionInput.getUnlockingScript(), context);
            Assert.assertTrue(outputIsUnlocked);

            transactions.add(signedTransaction);
        }

        stratumMineBlockTaskFactory.setBlockVersion(BlockHeader.VERSION);
        stratumMineBlockTaskFactory.setPreviousBlockHash(previousBlockHash);
        stratumMineBlockTaskFactory.setDifficulty(difficulty);
        stratumMineBlockTaskFactory.setCoinbaseTransaction(coinbaseTransaction, _totalExtraNonceByteCount);
        stratumMineBlockTaskFactory.setExtraNonce(_extraNonce);

        for (final Transaction transaction : transactions) {
            stratumMineBlockTaskFactory.addTransaction(transaction);
        }

        _stratumMineBlockTaskFactory = stratumMineBlockTaskFactory;
        _currentMineBlockTask = stratumMineBlockTaskFactory.buildMineBlockTask();
    }

    protected void _sendWork(final JsonSocket socketConnection, final Boolean abandonOldJobs) {
        _setDifficulty(socketConnection);

        final RequestMessage mineBlockRequest = _currentMineBlockTask.createRequest(abandonOldJobs);

        Logger.log("Sent: "+ mineBlockRequest.toString());
        socketConnection.write(new JsonProtocolMessage(mineBlockRequest));
    }

    protected void _setDifficulty(final JsonSocket socketConnection) {
        final RequestMessage mineBlockMessage = new RequestMessage(RequestMessage.ServerCommand.SET_DIFFICULTY.getValue());

        final Json parametersJson = new Json(true);
        parametersJson.add(_shareDifficulty); // Difficulty::getDifficultyRatio
        mineBlockMessage.setParameters(parametersJson);

        Logger.log("Sent: "+ mineBlockMessage.toString());
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
            resultJson.add(_extraNonce2ByteCount);
        }

        final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
        responseMessage.setResult(resultJson);

        Logger.log("Sent: "+ responseMessage);
        socketConnection.write(new JsonProtocolMessage(responseMessage));
    }

    protected void _handleAuthorizeMessage(final RequestMessage requestMessage, final JsonSocket socketConnection) {
        { // Respond with successful authorization...
            final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
            responseMessage.setResult(ResponseMessage.RESULT_TRUE);

            Logger.log("Sent: "+ responseMessage.toString());
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

        Boolean submissionWasAccepted = true;

        final Long taskIdLong = ByteUtil.bytesToLong(taskId.getBytes());
        final StratumMineBlockTask mineBlockTask = _currentMineBlockTask;
        if (mineBlockTask == null) {
            submissionWasAccepted = false;
        }

        if (mineBlockTask != null) {
            final Difficulty shareDifficulty = Difficulty.BASE_DIFFICULTY.divideBy(_shareDifficulty);

            final BlockHeader blockHeader = mineBlockTask.assembleBlockHeader(stratumNonce, stratumExtraNonce2, stratumTimestamp);
            final Sha256Hash hash = blockHeader.getHash();
            Logger.log(mineBlockTask.getDifficulty().getBytes());
            Logger.log(hash);
            Logger.log(shareDifficulty.getBytes());

            if (! shareDifficulty.isSatisfiedBy(hash)) {
                submissionWasAccepted = false;
                Logger.log("NOTICE: Share Difficulty not satisfied.");

                final RequestMessage newRequestMessage = mineBlockTask.createRequest(false);
                Logger.log("Resending Task: "+ newRequestMessage.toString());
                socketConnection.write(new JsonProtocolMessage(newRequestMessage));
            }
            else if (blockHeader.isValid()) {
                final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
                Logger.log("Valid Block: " + blockHeaderDeflater.toBytes(blockHeader));

                final BlockDeflater blockDeflater = new BlockDeflater();
                final Block block = mineBlockTask.assembleBlock(stratumNonce, stratumExtraNonce2, stratumTimestamp);
                Logger.log(blockDeflater.toBytes(block));
                BitcoinUtil.exitSuccess();
            }
        }

        final ResponseMessage blockAcceptedMessage = new MinerSubmitBlockResult(requestMessage.getId(), submissionWasAccepted);

        Logger.log("Sent: "+ blockAcceptedMessage.toString());
        socketConnection.write(new JsonProtocolMessage(blockAcceptedMessage));
    }

    public StratumMiner() {
        _extraNonce = _createRandomBytes(_extraNonceByteCount);

        File TEMP_FILE = null;
        try {
            TEMP_FILE = File.createTempFile("tmp", ".dat");
            TEMP_FILE.deleteOnExit();
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }

        final Configuration configuration = new Configuration(TEMP_FILE);
        final Configuration.ServerProperties serverProperties = configuration.getServerProperties();

        _stratumServerSocket = new StratumServerSocket(serverProperties.getStratumPort(), _threadPool);

        _stratumServerSocket.setSocketEventCallback(new StratumServerSocket.SocketEventCallback() {
            @Override
            public void onConnect(final JsonSocket socketConnection) {
                Logger.log("Node connected: " + socketConnection.getIp() + ":" + socketConnection.getPort());

                socketConnection.setMessageReceivedCallback(new Runnable() {
                    @Override
                    public void run() {
                        final JsonProtocolMessage jsonProtocolMessage = socketConnection.popMessage();
                        final Json message = jsonProtocolMessage.getMessage();

                        { // Handle Request Messages...
                            final RequestMessage requestMessage = RequestMessage.parse(message);
                            if (requestMessage != null) {
                                Logger.log("Received: " + requestMessage);

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
                                    Logger.log("Unrecognized Message: " + requestMessage.getCommand());
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
                Logger.log("Node disconnected: " + disconnectedSocket.getIp() + ":" + disconnectedSocket.getPort());
            }
        });
    }

    public void loop() {
        _buildMiningTask();

        _stratumServerSocket.start();

        Logger.log("[Server Online]");

        while (true) {
            try { Thread.sleep(60000); } catch (final Exception exception) { break; }
        }
    }
}
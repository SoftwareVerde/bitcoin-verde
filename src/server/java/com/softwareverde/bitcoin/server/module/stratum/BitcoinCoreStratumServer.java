package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnector;
import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.rpc.RpcNotification;
import com.softwareverde.bitcoin.rpc.RpcNotificationCallback;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.bitcoin.rpc.core.BitcoinCoreRpcConnector;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.stratum.callback.BlockFoundCallback;
import com.softwareverde.bitcoin.server.module.stratum.callback.WorkerShareCallback;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerSubmitBlockResult;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.server.stratum.task.MutableStratumMineBlockTaskBuilder;
import com.softwareverde.bitcoin.server.stratum.task.StratumMineBlockTask;
import com.softwareverde.bitcoin.server.stratum.task.StratumMineBlockTaskBuilderCore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;


public class BitcoinCoreStratumServer implements StratumServer {
    public static final String COINBASE_ADDRESS_KEY = "coinbase_address";

    protected static final Boolean PROXY_VIABTC = false;

    protected final MasterInflater _masterInflater;
    protected final StratumProperties _stratumProperties;
    protected final StratumServerSocket _stratumServerSocket;
    protected final ThreadPool _threadPool;
    protected final PropertiesStore _propertiesStore;

    protected final Integer _extraNonceByteCount = 4;
    protected final Integer _extraNonce2ByteCount = 4;
    protected final Integer _totalExtraNonceByteCount = (_extraNonceByteCount + _extraNonce2ByteCount);
    protected final ByteArray _seedBytes;

    protected final SystemTime _systemTime = new SystemTime();

    // Multi buffered maps to gradually purge old tasks between block template regeneration...
    protected ConcurrentHashMap<Long, StratumMineBlockTask> _mineBlockTasks = new ConcurrentHashMap<>(); // Map: JobId -> MineBlockTask
    protected ConcurrentHashMap<Long, StratumMineBlockTask> _olderMineBlockTasks0 = new ConcurrentHashMap<>(); // Newer
    protected ConcurrentHashMap<Long, StratumMineBlockTask> _olderMineBlockTasks1 = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<Long, StratumMineBlockTask> _olderMineBlockTasks2 = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<Long, StratumMineBlockTask> _olderMineBlockTasks3 = new ConcurrentHashMap<>(); // Older

    protected final Thread _rebuildBlockTemplateThread;
    protected BlockTemplate _blockTemplate;

    protected final Long _startTime = _systemTime.getCurrentTimeInSeconds();
    protected Long _currentBlockStartTime = _systemTime.getCurrentTimeInSeconds();
    protected final AtomicLong _shareCount = new AtomicLong(0L);

    protected final ConcurrentLinkedQueue<JsonSocket> _connections = new ConcurrentLinkedQueue<>();

    protected WorkerShareCallback _workerShareCallback;
    protected BlockFoundCallback _blockFoundCallback;

    protected Long _baseShareDifficulty = 2048L;

    protected Address _getCoinbaseAddress() {
        final AddressInflater addressInflater = new AddressInflater();
        final String addressString = _propertiesStore.getString(BitcoinCoreStratumServer.COINBASE_ADDRESS_KEY);
        return Util.coalesce(addressInflater.fromBase58Check(addressString), addressInflater.fromBase32Check(addressString));
    }

    protected synchronized void _rebuildBlockTemplate() {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        try (final BitcoinMiningRpcConnector rpcConnection = _getBitcoinRpcConnector()) {
            _blockTemplate = rpcConnection.getBlockTemplate();
        }

        nanoTimer.stop();
        Logger.trace("Acquired new block template in " + nanoTimer.getMillisecondsElapsed() + "ms.");
    }

    protected void _abandonMiningTasks() {
        _mineBlockTasks.clear();
        _olderMineBlockTasks0.clear();
        _olderMineBlockTasks1.clear();
        _olderMineBlockTasks2.clear();
        _olderMineBlockTasks3.clear();
    }

    protected synchronized void _deprecateMiningTasks() {
        _olderMineBlockTasks3.clear();
        _olderMineBlockTasks3.putAll(_olderMineBlockTasks2);

        _olderMineBlockTasks2.clear();
        _olderMineBlockTasks2.putAll(_olderMineBlockTasks1);

        _olderMineBlockTasks1.clear();
        _olderMineBlockTasks1.putAll(_olderMineBlockTasks0);

        _olderMineBlockTasks0.clear();
        _olderMineBlockTasks0.putAll(_mineBlockTasks);

        _mineBlockTasks.clear();
    }

    protected StratumMineBlockTask _getMineBlockTask(final Long taskId) {
        StratumMineBlockTask mineBlockTask;

        mineBlockTask = _mineBlockTasks.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        mineBlockTask = _olderMineBlockTasks0.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        mineBlockTask = _olderMineBlockTasks1.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        mineBlockTask = _olderMineBlockTasks2.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        mineBlockTask = _olderMineBlockTasks3.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        return null;
    }

    protected void _broadcastNewTask(final Boolean abandonOldJobs) {
        final Iterator<JsonSocket> iterator = _connections.iterator();
        while (iterator.hasNext()) {
            final JsonSocket jsonSocket = iterator.next();
            if (jsonSocket == null) { continue; }

            _sendWork(jsonSocket, abandonOldJobs);
        }
    }

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

    protected ByteArray _getExtraNonce(final Long jsonSocketId) {
        final ByteArray socketIdBytes = ByteArray.wrap(ByteUtil.longToBytes(jsonSocketId));
        final MutableByteArray extraNonce = new MutableByteArray(_seedBytes);
        final int byteCount = Math.min(socketIdBytes.getByteCount(), extraNonce.getByteCount());
        for (int i = 0; i < byteCount; ++i) {
            final byte byteA = extraNonce.getByte(i);
            final byte byteB = socketIdBytes.getByte(i);
            extraNonce.setByte(i, (byte) (byteA ^ byteB));
        }
        return extraNonce;
    }

    protected BitcoinMiningRpcConnector _getBitcoinRpcConnector() {
        final String bitcoinRpcUrl = _stratumProperties.getBitcoinRpcUrl();
        final Integer bitcoinRpcPort = _stratumProperties.getBitcoinRpcPort();

        final RpcCredentials rpcCredentials = _stratumProperties.getRpcCredentials();
        final BitcoinNodeRpcAddress bitcoinNodeRpcAddress = new BitcoinNodeRpcAddress(bitcoinRpcUrl, bitcoinRpcPort, false);

        return new BitcoinCoreRpcConnector(bitcoinNodeRpcAddress, rpcCredentials);
    }

    protected void _resetHashCountMonitoring() {
        _currentBlockStartTime = _systemTime.getCurrentTimeInSeconds();
        _shareCount.set(0L);
    }

    protected synchronized StratumMineBlockTask _buildNewMiningTask(final Long jsonSocketId) {
        final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
        final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final ByteArray extraNonce = _getExtraNonce(jsonSocketId);
        final MutableStratumMineBlockTaskBuilder stratumMineBlockTaskBuilder = new StratumMineBlockTaskBuilderCore(_totalExtraNonceByteCount, transactionDeflater);

        final String coinbaseMessage = BitcoinConstants.getCoinbaseMessage();

        final Address address = _getCoinbaseAddress();

        final BlockTemplate blockTemplate = _blockTemplate;
        final Long blockHeight = blockTemplate.getBlockHeight();
        final Sha256Hash previousBlockHash = blockTemplate.getPreviousBlockHash();
        final Difficulty difficulty = blockTemplate.getDifficulty();
        final List<Transaction> transactions = blockTemplate.getTransactions();
        final Long coinbaseAmount = blockTemplate.getCoinbaseAmount();

        // NOTE: Coinbase is mutated by the StratumMineTaskFactory to include the Transaction Fees...
        final Transaction coinbaseTransaction = transactionInflater.createCoinbaseTransactionWithExtraNonce(blockHeight, coinbaseMessage, _totalExtraNonceByteCount, address, coinbaseAmount);

        stratumMineBlockTaskBuilder.setBlockVersion(BlockHeader.VERSION);
        stratumMineBlockTaskBuilder.setPreviousBlockHash(previousBlockHash);
        stratumMineBlockTaskBuilder.setDifficulty(difficulty);
        stratumMineBlockTaskBuilder.setCoinbaseTransaction(coinbaseTransaction);
        stratumMineBlockTaskBuilder.setExtraNonce(extraNonce);
        stratumMineBlockTaskBuilder.setBlockHeight(blockHeight);

        stratumMineBlockTaskBuilder.setTransactions(transactions);

        final StratumMineBlockTask mineBlockTask = stratumMineBlockTaskBuilder.buildMineBlockTask();

        nanoTimer.stop();
        Logger.trace("Built mining task from prototype block in " + nanoTimer.getMillisecondsElapsed() + "ms.");

        final boolean templateValidationIsEnabled = false;
        if (templateValidationIsEnabled) { // Validate template...
            final NanoTimer validateTimer = new NanoTimer();
            validateTimer.start();
            final Block block = mineBlockTask.assembleBlockTemplate(_extraNonceByteCount, _extraNonce2ByteCount);
            final Boolean isValid;
            try (final BitcoinMiningRpcConnector rpcConnection = _getBitcoinRpcConnector()) {
                isValid = rpcConnection.validateBlockTemplate(block);
            }
            validateTimer.stop();
            Logger.trace("Validated block template (" + isValid + ") in " + validateTimer.getMillisecondsElapsed() + "ms.");
        }

        return mineBlockTask;
    }

    protected void _sendWork(final JsonSocket jsonSocket, final Boolean abandonOldJobs) {
        final Long jsonSocketId = jsonSocket.getId();

        final StratumMineBlockTask mineBlockTask = _buildNewMiningTask(jsonSocketId);
        final Long mineBlockTaskId = mineBlockTask.getId();
        _mineBlockTasks.put(mineBlockTaskId, mineBlockTask);

        final RequestMessage mineBlockRequest = mineBlockTask.createRequest(abandonOldJobs);

        Logger.debug("Sent: "+ mineBlockRequest.toString());
        jsonSocket.write(new JsonProtocolMessage(mineBlockRequest));
    }

    protected void _setDifficulty(final JsonSocket socketConnection) {
        final Long shareDifficulty = _baseShareDifficulty;

        final RequestMessage mineBlockMessage = new RequestMessage(RequestMessage.ServerCommand.SET_DIFFICULTY.getValue());
        final Json parametersJson = new Json(true);
        parametersJson.add(shareDifficulty); // Difficulty::getDifficultyRatio
        mineBlockMessage.setParameters(parametersJson);

        Logger.debug("Sent: "+ mineBlockMessage);
        socketConnection.write(new JsonProtocolMessage(mineBlockMessage));
    }

    protected void _handleSubscribeMessage(final RequestMessage requestMessage, final JsonSocket jsonSocket) {
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

            final Long jsonSocketId = jsonSocket.getId();
            final ByteArray extraNonce = _getExtraNonce(jsonSocketId);
            resultJson.add(extraNonce);

            resultJson.add(_extraNonce2ByteCount);
        }

        final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
        responseMessage.setResult(resultJson);

        Logger.debug("Sent: "+ responseMessage);
        jsonSocket.write(new JsonProtocolMessage(responseMessage));
    }

    protected void _handleAuthorizeMessage(final RequestMessage requestMessage, final JsonSocket socketConnection) {
        { // Respond with successful authorization...
            final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
            responseMessage.setResult(ResponseMessage.RESULT_TRUE);

            Logger.debug("Sent: "+ responseMessage);
            socketConnection.write(new JsonProtocolMessage(responseMessage));
        }

        _sendWork(socketConnection, true);
    }

    protected void _handleSubmitMessage(final RequestMessage requestMessage, final JsonSocket jsonSocket) {
        // mining.submit("username", "job id", "ExtraNonce2", "nTime", "nOnce")

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final Integer messageId = requestMessage.getId();
        final Json messageParameters = requestMessage.getParameters();
        final String workerUsername = messageParameters.getString(0);
        final ByteArray taskId = MutableByteArray.wrap(HexUtil.hexStringToByteArray(messageParameters.getString(1)));
        final String stratumNonce = messageParameters.getString(4);
        final String stratumExtraNonce2 = messageParameters.getString(2);
        final String stratumTimestamp = messageParameters.getString(3);

        final Long taskIdLong = ByteUtil.bytesToLong(taskId.getBytes());
        final StratumMineBlockTask mineBlockTask = _getMineBlockTask(taskIdLong);

        if (mineBlockTask == null) {
            final ResponseMessage responseMessage = new MinerSubmitBlockResult(messageId, ResponseMessage.Error.NOT_FOUND);
            Logger.debug("Sent: " + responseMessage);
            jsonSocket.write(new JsonProtocolMessage(responseMessage));
            return;
        }

        final Long baseShareDifficulty = _baseShareDifficulty;
        final Difficulty shareDifficulty = Difficulty.BASE_DIFFICULTY.divideBy(baseShareDifficulty);

        final BlockHeader blockHeader = mineBlockTask.assembleBlockHeader(stratumNonce, stratumExtraNonce2, stratumTimestamp);
        final Sha256Hash blockHash = blockHeader.getHash();
        Logger.debug(workerUsername + ": " + blockHash);

        if (! shareDifficulty.isSatisfiedBy(blockHash)) {
            Logger.warn("Share Difficulty not satisfied.");

            final ResponseMessage responseMessage = new MinerSubmitBlockResult(messageId, ResponseMessage.Error.LOW_DIFFICULTY);
            Logger.debug("Sent: " + responseMessage);
            jsonSocket.write(new JsonProtocolMessage(responseMessage));

            _setDifficulty(jsonSocket);
            _sendWork(jsonSocket, true);
            return;
        }

        if (blockHeader.isValid()) {
            final BlockHeaderDeflater blockHeaderDeflater = _masterInflater.getBlockHeaderDeflater();
            Logger.info("Valid Block: " + blockHeaderDeflater.toBytes(blockHeader));

            final BlockDeflater blockDeflater = _masterInflater.getBlockDeflater();
            final Block block = mineBlockTask.assembleBlock(stratumNonce, stratumExtraNonce2, stratumTimestamp);
            Logger.info(blockDeflater.toBytes(block));

            final BitcoinMiningRpcConnector bitcoinRpcConnector = _getBitcoinRpcConnector();
            final Boolean submitBlockResponse = bitcoinRpcConnector.submitBlock(block);
            if (! submitBlockResponse) {
                Logger.warn("Unable to submit block: " + blockHash);
            }

            _rebuildBlockTemplate();
            _deprecateMiningTasks();
            _resetHashCountMonitoring();
            _broadcastNewTask(true);

            final BlockFoundCallback blockFoundCallback = _blockFoundCallback;
            if (blockFoundCallback != null) {
                blockFoundCallback.run(block, workerUsername);
            }
        }

        _shareCount.incrementAndGet();

        final WorkerShareCallback workerShareCallback = _workerShareCallback;
        if (workerShareCallback != null) {
            final Boolean wasAccepted = workerShareCallback.onNewWorkerShare(workerUsername, baseShareDifficulty, blockHash);
            if (! wasAccepted) {
                final ResponseMessage responseMessage = new MinerSubmitBlockResult(messageId, ResponseMessage.Error.DUPLICATE);
                Logger.debug("Sent: " + responseMessage);
                jsonSocket.write(new JsonProtocolMessage(responseMessage));
            }
        }

        final ResponseMessage shareAcceptedMessage = new MinerSubmitBlockResult(messageId);
        jsonSocket.write(new JsonProtocolMessage(shareAcceptedMessage));

        nanoTimer.stop();
        Logger.trace("Accepted share in " + nanoTimer.getMillisecondsElapsed() + "ms.");
    }

    public BitcoinCoreStratumServer(final StratumProperties stratumProperties, final PropertiesStore propertiesStore, final ThreadPool threadPool, final MasterInflater masterInflater) {
        _masterInflater = masterInflater;
        _stratumProperties = stratumProperties;
        _threadPool = threadPool;
        _seedBytes = _createRandomBytes(_extraNonceByteCount);

        _propertiesStore = propertiesStore;

        final Integer stratumPort = stratumProperties.getPort();
        _stratumServerSocket = new StratumServerSocket(stratumPort, _threadPool);

        _stratumServerSocket.setSocketEventCallback(new StratumServerSocket.SocketEventCallback() {
            @Override
            public void onConnect(final JsonSocket jsonSocket) {
                Logger.debug("Node connected: " + jsonSocket.getIp() + ":" + jsonSocket.getPort());
                _connections.add(jsonSocket);

                if (PROXY_VIABTC) {
                    try {
                        final JsonSocket viaBtcSocket = new JsonSocket(new Socket("bch.viabtc.com", 3333), _threadPool);

                        viaBtcSocket.setMessageReceivedCallback(new Runnable() {
                            @Override
                            public void run() {
                                final JsonProtocolMessage jsonProtocolMessage = viaBtcSocket.popMessage();
                                final Json message = jsonProtocolMessage.getMessage();
                                Logger.trace("VIABTC SENT: " + message);

                                jsonSocket.write(jsonProtocolMessage);
                            }
                        });

                        jsonSocket.setMessageReceivedCallback(new Runnable() {
                            @Override
                            public void run() {
                                final JsonProtocolMessage jsonProtocolMessage = jsonSocket.popMessage();
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
                else {
                    jsonSocket.setMessageReceivedCallback(new Runnable() {
                        @Override
                        public void run() {
                            final JsonProtocolMessage jsonProtocolMessage = jsonSocket.popMessage();
                            final Json message = jsonProtocolMessage.getMessage();

                            { // Handle Request Messages...
                                final RequestMessage requestMessage = RequestMessage.parse(message);
                                if (requestMessage != null) {
                                    Logger.trace("Received: " + requestMessage);

                                    if (requestMessage.isCommand(RequestMessage.ClientCommand.SUBSCRIBE)) {
                                        _handleSubscribeMessage(requestMessage, jsonSocket);
                                        _setDifficulty(jsonSocket); // Redundant difficulty message due to miners ignoring the difficulty sent in subscribe response...
                                    }
                                    else if (requestMessage.isCommand(RequestMessage.ClientCommand.AUTHORIZE)) {
                                        _handleAuthorizeMessage(requestMessage, jsonSocket);
                                        _setDifficulty(jsonSocket);
                                    }
                                    else if (requestMessage.isCommand(RequestMessage.ClientCommand.SUBMIT)) {
                                        _handleSubmitMessage(requestMessage, jsonSocket);
                                    }
                                    else {
                                        Logger.debug("Unrecognized Message: " + requestMessage.getCommand());
                                    }
                                }
                            }

                            { // Handle Response Messages...
                                final ResponseMessage responseMessage = ResponseMessage.parse(message);
                                if (responseMessage != null) { Logger.debug(responseMessage); }
                            }
                        }
                    });
                }

                jsonSocket.beginListening();
            }

            @Override
            public void onDisconnect(final JsonSocket disconnectedSocket) {
                Logger.debug("Node disconnected: " + disconnectedSocket.getIp() + ":" + disconnectedSocket.getPort());

                final Iterator<JsonSocket> iterator = _connections.iterator();
                while (iterator.hasNext()) {
                    final JsonSocket jsonSocket = iterator.next();
                    if (jsonSocket == null) { continue; }

                    if (Util.areEqual(disconnectedSocket, jsonSocket)) {
                        iterator.remove();
                        break;
                    }
                }
            }
        });

        _rebuildBlockTemplateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (! Thread.interrupted()) {
                    try { Thread.sleep(15000); } catch (final InterruptedException exception) { break; }

                    _rebuildBlockTemplate();
                    _deprecateMiningTasks();
                    _broadcastNewTask(false);
                }
            }
        });
        _rebuildBlockTemplateThread.setName("StratumServer - Block Template Thread");
        _rebuildBlockTemplateThread.setDaemon(true);
        _rebuildBlockTemplateThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
    }

    @Override
    public void start() {
        _rebuildBlockTemplate();

        final BitcoinMiningRpcConnector notificationBitcoinRpcConnector = _getBitcoinRpcConnector();
        notificationBitcoinRpcConnector.subscribeToNotifications(new RpcNotificationCallback() {
            @Override
            public void onNewNotification(final RpcNotification rpcNotification) {
                final Boolean useBlockHashAsAnnouncementHook = notificationBitcoinRpcConnector.supportsNotification(RpcNotificationType.BLOCK_HASH);

                switch (rpcNotification.rpcNotificationType) {
                    case BLOCK: {
                        if (useBlockHashAsAnnouncementHook) {
                            break;
                        }
                    } // Fallthrough...
                    case BLOCK_HASH: {
                        Logger.info("New Block received.");
                        _rebuildBlockTemplate();
                        _abandonMiningTasks();
                        _resetHashCountMonitoring();
                        _broadcastNewTask(true);
                    }
                }
            }
        });

        _stratumServerSocket.start();

        Logger.info("[Server Online]");
        Logger.debug("Coinbase Address: " + _getCoinbaseAddress().toBase58CheckEncoded());

        _rebuildBlockTemplateThread.start();
    }

    @Override
    public void stop() {
        _rebuildBlockTemplateThread.interrupt();
        try { _rebuildBlockTemplateThread.join(15000L); } catch (final Exception exception) { }

        _stratumServerSocket.stop();
    }

    @Override
    public Block getPrototypeBlock() {
        final StratumMineBlockTask mineBlockTask = _buildNewMiningTask(0L);
        return mineBlockTask.assembleBlockTemplate(_extraNonceByteCount, _extraNonce2ByteCount);
    }

    @Override
    public Long getBlockHeight() {
        final BlockTemplate blockTemplate = _blockTemplate;
        return blockTemplate.getBlockHeight();
    }

    @Override
    public Long getShareDifficulty() {
        return _baseShareDifficulty;
    }

    @Override
    public void setShareDifficulty(final Long baseShareDifficulty) {
        _baseShareDifficulty = baseShareDifficulty;
        _resetHashCountMonitoring();
        for (final JsonSocket jsonSocket : _connections) {
            _setDifficulty(jsonSocket);
        }
        _broadcastNewTask(true);
    }

    @Override
    public Long getShareCount() {
        return _shareCount.get();
    }

    @Override
    public Long getStartTimeInSeconds() {
        return _startTime;
    }

    @Override
    public Long getCurrentBlockStartTimeInSeconds() { return _currentBlockStartTime; }

    @Override
    public void setWorkerShareCallback(final WorkerShareCallback workerShareCallback) {
        _workerShareCallback = workerShareCallback;
    }

    @Override
    public void setBlockFoundCallback(final BlockFoundCallback blockFoundCallback) {
        _blockFoundCallback = blockFoundCallback;
    }
}
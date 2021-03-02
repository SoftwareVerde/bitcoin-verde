package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.rpc.RpcNotification;
import com.softwareverde.bitcoin.rpc.RpcNotificationCallback;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.bitcoin.rpc.core.BitcoinCoreRpcConnector;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerSubmitBlockResult;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.server.stratum.task.MutableStratumMineBlockTaskBuilder;
import com.softwareverde.bitcoin.server.stratum.task.StagnantStratumMineBlockTaskBuilderCore;
import com.softwareverde.bitcoin.server.stratum.task.StagnantStratumMineBlockTaskBuilderFactory;
import com.softwareverde.bitcoin.server.stratum.task.StratumMineBlockTask;
import com.softwareverde.bitcoin.server.stratum.task.StratumMineBlockTaskBuilder;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class BitcoinCoreStratumServer implements StratumServer {
    protected static final Boolean PROXY_VIABTC = false;

    protected final MasterInflater _masterInflater;
    protected final StratumProperties _stratumProperties;
    protected final StratumServerSocket _stratumServerSocket;
    protected final ThreadPool _threadPool;
    protected final StagnantStratumMineBlockTaskBuilderFactory _stratumMineBlockTaskBuilderFactory;

    protected final PrivateKey _privateKey;

    protected final Integer _extraNonceByteCount = 4;
    protected final Integer _extraNonce2ByteCount = 4;
    protected final Integer _totalExtraNonceByteCount = (_extraNonceByteCount + _extraNonce2ByteCount);
    protected final ByteArray _extraNonce;

    protected final SystemTime _systemTime = new SystemTime();

    protected final ReentrantReadWriteLock.WriteLock _mineBlockTaskWriteLock;
    protected final ReentrantReadWriteLock.ReadLock _mineBlockTaskReadLock;
    protected MutableStratumMineBlockTaskBuilder _stratumMineBlockTaskBuilder;
    protected StratumMineBlockTask _currentMineBlockTask = null;
    protected final ConcurrentHashMap<Long, StratumMineBlockTask> _mineBlockTasks = new ConcurrentHashMap<Long, StratumMineBlockTask>();

    protected MilliTimer _lastTransactionQueueProcessTimer = new MilliTimer();
    protected final ConcurrentLinkedQueue<TransactionWithFee> _queuedTransactions = new ConcurrentLinkedQueue<TransactionWithFee>();

    protected final Integer _shareDifficulty = 2048;

    protected final Thread _rebuildTaskThread;

    protected final Long _startTime = _systemTime.getCurrentTimeInSeconds();
    protected Long _currentBlockStartTime = _systemTime.getCurrentTimeInSeconds();
    protected final AtomicLong _shareCount = new AtomicLong(0L);

    protected final ConcurrentLinkedQueue<JsonSocket> _connections = new ConcurrentLinkedQueue<JsonSocket>();

    protected WorkerShareCallback _workerShareCallback;

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

    protected BitcoinCoreRpcConnector _getBitcoinRpcConnector() {
        final String bitcoinRpcUrl = _stratumProperties.getBitcoinRpcUrl();
        final Integer bitcoinRpcPort = _stratumProperties.getBitcoinRpcPort();

        final RpcCredentials rpcCredentials = _stratumProperties.getRpcCredentials();
        final BitcoinNodeRpcAddress bitcoinNodeRpcAddress = new BitcoinNodeRpcAddress(bitcoinRpcUrl, bitcoinRpcPort, false);

        return new BitcoinCoreRpcConnector(bitcoinNodeRpcAddress, rpcCredentials);
    }

    protected Block _assemblePrototypeBlock(final StratumMineBlockTaskBuilder stratumMineBlockTaskBuilder) {
        final StratumMineBlockTask mineBlockTask = stratumMineBlockTaskBuilder.buildMineBlockTask();
        final String zeroes = Sha256Hash.EMPTY_HASH.toString();
        final String stratumNonce = zeroes.substring(0, (4 * 2));
        final String stratumExtraNonce2 = zeroes.substring(0, (_extraNonce2ByteCount * 2));
        final String stratumTimestamp = HexUtil.toHexString(ByteUtil.longToBytes(mineBlockTask.getTimestamp()));
        return mineBlockTask.assembleBlock(stratumNonce, stratumExtraNonce2, stratumTimestamp);
    }

    protected void _rebuildNewMiningTask() {
        final StagnantStratumMineBlockTaskBuilderCore stratumMineBlockTaskBuilder = _stratumMineBlockTaskBuilderFactory.newStratumMineBlockTaskBuilder(_totalExtraNonceByteCount);

        final String coinbaseMessage = BitcoinConstants.getCoinbaseMessage();

        final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
        final AddressInflater addressInflater = _masterInflater.getAddressInflater();
        final Address address = addressInflater.fromPrivateKey(_privateKey, true);

        final BitcoinCoreRpcConnector bitcoinRpcConnector = _getBitcoinRpcConnector();
        final BlockTemplate blockTemplate = bitcoinRpcConnector.getBlockTemplate();

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
        stratumMineBlockTaskBuilder.setExtraNonce(_extraNonce);
        stratumMineBlockTaskBuilder.setBlockHeight(blockHeight);

        stratumMineBlockTaskBuilder.clearTransaction();
        stratumMineBlockTaskBuilder.addTransactions(transactions);

        try {
            _mineBlockTaskWriteLock.lock();

            _stratumMineBlockTaskBuilder = stratumMineBlockTaskBuilder;
            _currentMineBlockTask = stratumMineBlockTaskBuilder.buildMineBlockTask();
            _mineBlockTasks.clear();
            _mineBlockTasks.put(_currentMineBlockTask.getId(), _currentMineBlockTask);

            _queuedTransactions.clear();
            _lastTransactionQueueProcessTimer.reset();
        }
        finally {
            _mineBlockTaskWriteLock.unlock();
        }

        _currentBlockStartTime = _systemTime.getCurrentTimeInSeconds();
        _shareCount.set(0L);
    }

    /**
     * Builds a new task from the factory.  New tasks have a new timestamp and include any new transactions added to the factory.
     */
    protected void _updateCurrentMiningTask() {
        try {
            _mineBlockTaskWriteLock.lock();

            final StratumMineBlockTask stratumMineBlockTask = _stratumMineBlockTaskBuilder.buildMineBlockTask();
            _currentMineBlockTask = stratumMineBlockTask;
            _mineBlockTasks.put(stratumMineBlockTask.getId(), stratumMineBlockTask);
        }
        finally {
            _mineBlockTaskWriteLock.unlock();
        }
    }

    protected void _sendWork(final JsonSocket socketConnection, final Boolean abandonOldJobs) {
        _setDifficulty(socketConnection);

        final RequestMessage mineBlockRequest;
        try {
            _mineBlockTaskReadLock.lock();

            mineBlockRequest = _currentMineBlockTask.createRequest(abandonOldJobs);
        }
        finally {
            _mineBlockTaskReadLock.unlock();
        }

        Logger.debug("Sent: "+ mineBlockRequest.toString());
        socketConnection.write(new JsonProtocolMessage(mineBlockRequest));
    }

    protected void _setDifficulty(final JsonSocket socketConnection) {
        final RequestMessage mineBlockMessage = new RequestMessage(RequestMessage.ServerCommand.SET_DIFFICULTY.getValue());

        final Json parametersJson = new Json(true);
        parametersJson.add(_shareDifficulty); // Difficulty::getDifficultyRatio
        mineBlockMessage.setParameters(parametersJson);

        Logger.debug("Sent: "+ mineBlockMessage.toString());
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
            try {
                _mineBlockTaskReadLock.lock();

                resultJson.add(_currentMineBlockTask.getExtraNonce());
            }
            finally {
                _mineBlockTaskReadLock.unlock();
            }
            resultJson.add(_extraNonce2ByteCount);
        }

        final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
        responseMessage.setResult(resultJson);

        Logger.debug("Sent: "+ responseMessage);
        socketConnection.write(new JsonProtocolMessage(responseMessage));
    }

    protected void _handleAuthorizeMessage(final RequestMessage requestMessage, final JsonSocket socketConnection) {
        { // Respond with successful authorization...
            final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
            responseMessage.setResult(ResponseMessage.RESULT_TRUE);

            Logger.debug("Sent: "+ responseMessage.toString());
            socketConnection.write(new JsonProtocolMessage(responseMessage));
        }

        _sendWork(socketConnection, true);
    }

    protected void _handleSubmitMessage(final RequestMessage requestMessage, final JsonSocket socketConnection) {
        // mining.submit("username", "job id", "ExtraNonce2", "nTime", "nOnce")

        final Json messageParameters = requestMessage.getParameters();
        final String workerUsername = messageParameters.getString(0);
        final ByteArray taskId = MutableByteArray.wrap(HexUtil.hexStringToByteArray(messageParameters.getString(1)));
        final String stratumNonce = messageParameters.getString(4);
        final String stratumExtraNonce2 = messageParameters.getString(2);
        final String stratumTimestamp = messageParameters.getString(3);

        boolean submissionWasAccepted = true;

        final Long taskIdLong = ByteUtil.bytesToLong(taskId.getBytes());
        final StratumMineBlockTask mineBlockTask;
        try {
            _mineBlockTaskReadLock.lock();

            mineBlockTask = _mineBlockTasks.get(taskIdLong);
        }
        finally {
            _mineBlockTaskReadLock.unlock();
        }
        if (mineBlockTask == null) {
            submissionWasAccepted = false;
        }

        if (mineBlockTask != null) {
            final Difficulty shareDifficulty = Difficulty.BASE_DIFFICULTY.divideBy(_shareDifficulty);

            final BlockHeader blockHeader = mineBlockTask.assembleBlockHeader(stratumNonce, stratumExtraNonce2, stratumTimestamp);
            final Sha256Hash hash = blockHeader.getHash();
            Logger.debug(workerUsername + ": " + hash);
            if (! shareDifficulty.isSatisfiedBy(hash)) {
                submissionWasAccepted = false;
                Logger.warn("Share Difficulty not satisfied.");

                final RequestMessage newRequestMessage = mineBlockTask.createRequest(false);
                Logger.debug("Resending Task: "+ newRequestMessage.toString());
                socketConnection.write(new JsonProtocolMessage(newRequestMessage));
            }
            else if (blockHeader.isValid()) {
                final BlockHeaderDeflater blockHeaderDeflater = _masterInflater.getBlockHeaderDeflater();
                Logger.info("Valid Block: " + blockHeaderDeflater.toBytes(blockHeader));

                final BlockDeflater blockDeflater = _masterInflater.getBlockDeflater();
                final Block block = mineBlockTask.assembleBlock(stratumNonce, stratumExtraNonce2, stratumTimestamp);
                Logger.info(blockDeflater.toBytes(block));

                final BitcoinCoreRpcConnector bitcoinRpcConnector = _getBitcoinRpcConnector();
                final Boolean submitBlockResponse = bitcoinRpcConnector.submitBlock(block);
                if (! submitBlockResponse) {
                    submissionWasAccepted = false;
                }

                _rebuildNewMiningTask();
                _broadcastNewTask(true);
            }
        }

        if (submissionWasAccepted) {
            _shareCount.incrementAndGet();

            final WorkerShareCallback workerShareCallback = _workerShareCallback;
            if (workerShareCallback != null) {
                _threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        workerShareCallback.onNewWorkerShare(workerUsername, _shareDifficulty);
                    }
                });
            }
        }

        final ResponseMessage blockAcceptedMessage = new MinerSubmitBlockResult(requestMessage.getId(), submissionWasAccepted);

        Logger.debug("Sent: "+ blockAcceptedMessage.toString());
        socketConnection.write(new JsonProtocolMessage(blockAcceptedMessage));
    }

    public BitcoinCoreStratumServer(final StratumProperties stratumProperties, final ThreadPool threadPool) {
        this(
            stratumProperties,
            threadPool,
            new CoreInflater()
        );
    }

    public BitcoinCoreStratumServer(final StratumProperties stratumProperties, final ThreadPool threadPool, final MasterInflater masterInflater) {
        this(
            stratumProperties,
            threadPool,
            masterInflater,
            new StagnantStratumMineBlockTaskBuilderFactory() {
                @Override
                public StagnantStratumMineBlockTaskBuilderCore newStratumMineBlockTaskBuilder(final Integer totalExtraNonceByteCount) {
                    return new StagnantStratumMineBlockTaskBuilderCore(totalExtraNonceByteCount, masterInflater.getTransactionDeflater());
                }
            }
        );
    }

    public BitcoinCoreStratumServer(final StratumProperties stratumProperties, final ThreadPool threadPool, final MasterInflater masterInflater, final StagnantStratumMineBlockTaskBuilderFactory stratumMineBlockTaskBuilderFactory) {
        _stratumMineBlockTaskBuilderFactory = stratumMineBlockTaskBuilderFactory;
        _masterInflater = masterInflater;
        _stratumProperties = stratumProperties;
        _threadPool = threadPool;

        final AddressInflater addressInflater = _masterInflater.getAddressInflater();

        _privateKey = PrivateKey.createNewKey();
        Logger.info("Private Key: " + _privateKey);
        Logger.info("Address:     " + addressInflater.fromPrivateKey(_privateKey, true).toBase58CheckEncoded());

        _extraNonce = _createRandomBytes(_extraNonceByteCount);

        _stratumServerSocket = new StratumServerSocket(stratumProperties.getPort(), _threadPool);

        _stratumServerSocket.setSocketEventCallback(new StratumServerSocket.SocketEventCallback() {
            @Override
            public void onConnect(final JsonSocket socketConnection) {
                Logger.debug("Node connected: " + socketConnection.getIp() + ":" + socketConnection.getPort());
                _connections.add(socketConnection);

                if (PROXY_VIABTC) {
                    try {
                        final JsonSocket viaBtcSocket = new JsonSocket(new Socket("bch.viabtc.com", 3333), _threadPool);

                        viaBtcSocket.setMessageReceivedCallback(new Runnable() {
                            @Override
                            public void run() {
                                final JsonProtocolMessage jsonProtocolMessage = viaBtcSocket.popMessage();
                                final Json message = jsonProtocolMessage.getMessage();
                                Logger.trace("VIABTC SENT: " + message);

                                socketConnection.write(jsonProtocolMessage);
                            }
                        });

                        socketConnection.setMessageReceivedCallback(new Runnable() {
                            @Override
                            public void run() {
                                final JsonProtocolMessage jsonProtocolMessage = socketConnection.popMessage();
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
                    socketConnection.setMessageReceivedCallback(new Runnable() {
                        @Override
                        public void run() {
                            final JsonProtocolMessage jsonProtocolMessage = socketConnection.popMessage();
                            final Json message = jsonProtocolMessage.getMessage();

                            { // Handle Request Messages...
                                final RequestMessage requestMessage = RequestMessage.parse(message);
                                if (requestMessage != null) {
                                    Logger.trace("Received: " + requestMessage);

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

                socketConnection.beginListening();
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

        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _mineBlockTaskWriteLock = readWriteLock.writeLock();
        _mineBlockTaskReadLock = readWriteLock.readLock();

        _rebuildTaskThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (! Thread.interrupted()) {
                    try { Thread.sleep(60000); } catch (final InterruptedException exception) { break; }

                    _updateCurrentMiningTask();
                    _broadcastNewTask(false);
                }
            }
        });
    }

    @Override
    public void start() {
        _rebuildNewMiningTask();

        final BitcoinCoreRpcConnector notificationBitcoinRpcConnector = _getBitcoinRpcConnector();
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
                        _rebuildNewMiningTask();
                        _broadcastNewTask(true);
                    }
                }
            }
        });

        _stratumServerSocket.start();

        Logger.info("[Server Online]");

        _rebuildTaskThread.start();
    }

    @Override
    public void stop() {
        _rebuildTaskThread.interrupt();
        try { _rebuildTaskThread.join(15000L); } catch (final Exception exception) { }

        _stratumServerSocket.stop();
    }

    @Override
    public Block getPrototypeBlock() {
        return _assemblePrototypeBlock(_stratumMineBlockTaskBuilder);
    }

    @Override
    public Long getBlockHeight() {
        return _stratumMineBlockTaskBuilder.getBlockHeight();
    }

    @Override
    public Integer getShareDifficulty() {
        return _shareDifficulty;
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
}
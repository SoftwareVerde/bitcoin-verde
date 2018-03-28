package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.stratum.StratumMinerSubmitResult;
import com.softwareverde.bitcoin.server.stratum.client.message.SubscribeMessage;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.client.AuthorizeMessage;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.socket.SocketConnection;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Container;

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

        if (false) try {
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

//        final PrivateKey privateKey = PrivateKey.createNewKey();
//        final ByteArray extraNonce = _createRandomBytes(4);

        _stratumServerSocket = new StratumServerSocket(serverProperties.getStratumPort());
        final Container<SocketConnection> asicSocketContainer = new Container<SocketConnection>();
        final Container<SocketConnection> viaBtcSocketContainer = new Container<SocketConnection>();

        _stratumServerSocket.setSocketEventCallback(new StratumServerSocket.SocketEventCallback() {

            private void _setupViaBtcSocket() {
                if (viaBtcSocketContainer.value == null) {
                    SocketConnection socketConnection = null;
                    try {
                        socketConnection = new SocketConnection(new Socket("bch.viabtc.com", 3333));
                        System.out.println("Connected to ViaBTC.");

                        socketConnection.setMessageReceivedCallback(new Runnable() {
                            @Override
                            public void run() {
                                final SocketConnection asicSocket = asicSocketContainer.value;
                                if (asicSocket != null) {
                                    String message;
                                    while ((message = viaBtcSocketContainer.value.popMessage()) != null) {
                                        System.out.println("From ViaBtc / To ASIC: "+ message);
                                        asicSocket.write(message);

                                        if (message.contains("mining.notify") && message.contains("result")) {
                                            final StratumMinerSubmitResult stratumMinerSubmitResult = _stratumMinerSubmitResult.value;
                                            // From ViaBtc / To ASIC: {"error": null, "id": 79, "result": [[["mining.set_difficulty", "9f890694d4fa5a48"], ["mining.notify", "9f890694d4fa5a48"]], "6c9f6fca", 4]}
                                            final Json json = Json.parse(message);
                                            final String stratumExtraNonce = json.get("result").getString(1);
                                            stratumMinerSubmitResult.setExtraNonce(stratumExtraNonce);
                                        }
                                        else if (message.contains("mining.notify")) {
                                            final StratumMinerSubmitResult stratumMinerSubmitResult = _stratumMinerSubmitResult.value;
                                            // From ViaBtc / To ASIC: {"id": null, "method": "mining.notify", "params": ["31a4", "2470216e12fbc4d6ce6e6063c2dc24342f6aad2701ed325f0000000000000000", "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff600396fa071d2f5669614254432f4d696e6564206279206d616b6f696e66757365642f2cfabe6d6d570691fcc1b41b8f6e478eab6b025f08785495c8f208097edacc3e7d46bb044a010000000000000010a431830cc9becd84", "ffffffff01487f8a4a000000001976a914f1c075a01882ae0972f95d3a4177c86c852b7d9188ac00000000", ["4104af23f45418605912a7cda01ddadbf78b601f363d4d96769e0873c39bac42", "08a265867a7d08c690900830641d0818bc7d039856fa29a3ca593ff3b9375f92", "a595fe57b6903fae5b67e09d411e958223fd318571b1a08fba5a5f7632c0e6fd", "0631f93fcd6bef286c55e9c63b43b3c0a97a5644c598b4aaa4c4a6096c690c96", "0a254b2f42928a1c46aecb1f91894fbd5a95c4a917567e4a7b0fc5c3f6f42bc1", "63e06c87c975b82320c92a920db57a0a98f7915e86e801666ec6a8e9e542723b", "c4920779c2a0ab98e7dccd2590ad009ee12dde60aea78247ae86d1ba5b26e61f"], "20000000", "1802e8d7", "5ab71476", true]}
                                            final Json json = Json.parse(message);

                                            final String stratumPreviousBlockHash = json.get("params").getString(1);
                                            stratumMinerSubmitResult.setPreviousBlockHash(stratumPreviousBlockHash);

                                            final String stratumCoinbaseHead = json.get("params").getString(2);
                                            final String stratumCoinbaseTail = json.get("params").getString(3);
                                            stratumMinerSubmitResult.setCoinbaseTransaction(stratumCoinbaseHead, stratumCoinbaseTail);

                                            final List<String> stratumMerkleTreeBranches;
                                            {
                                                final Json stratumMerkleTreeBranchesJson = json.get("params").get(4);
                                                final ImmutableListBuilder<String> listBuilder = new ImmutableListBuilder<String>(stratumMerkleTreeBranchesJson.length());
                                                for (int i=0; i<stratumMerkleTreeBranchesJson.length(); ++i) {
                                                    listBuilder.add(stratumMerkleTreeBranchesJson.getString(i));
                                                }
                                                stratumMerkleTreeBranches = listBuilder.build();
                                            }
                                            stratumMinerSubmitResult.setMerkleTreeBranches(stratumMerkleTreeBranches);

                                            final String stratumBlockVersion = json.get("params").getString(5);
                                            stratumMinerSubmitResult.setBlockVersion(stratumBlockVersion);

                                            final String stratumDifficulty = json.get("params").getString(6);
                                            stratumMinerSubmitResult.setDifficulty(stratumDifficulty);

                                            // final String stratumTimestamp = json.get("params").getString(7);
                                            // stratumMinerSubmitResult.setTimestamp(stratumDifficulty);
                                        }
                                    }
                                }
                            }
                        });
                    } catch (final Exception exception) { }
                    viaBtcSocketContainer.value = socketConnection;
                }
            }

            private final Container<StratumMinerSubmitResult> _stratumMinerSubmitResult = new Container<StratumMinerSubmitResult>(new StratumMinerSubmitResult());

            @Override
            public void onConnect(final SocketConnection socketConnection) {
                asicSocketContainer.value = socketConnection;
                Logger.log("Node connected.");

                socketConnection.setMessageReceivedCallback(new Runnable() {
                    @Override
                    public void run() {
                        final String message = socketConnection.popMessage();
                        _setupViaBtcSocket();
                        System.out.println("From ASIC / To ViaBTC: "+ message);
                        viaBtcSocketContainer.value.write(message);

                        if (message.contains("mining.submit")) {
                            final Json json = Json.parse(message);

                            final String extraNonce2 = json.get("params").getString(2);
                            final String timestamp = json.get("params").getString(3);
                            final String nonce = json.get("params").getString(4);

                            final BlockHeader blockHeader = _stratumMinerSubmitResult.value.assembleBlockHeader(nonce, extraNonce2, timestamp);

                            final Hash hash = blockHeader.getHash();
                            System.out.println("Block Hash: "+ hash);
                        }

//                        { // Handle Request Messages...
//                            final RequestMessage requestMessage = RequestMessage.parse(message);
//                            if (requestMessage != null) {
//                                Logger.log("Received: "+ requestMessage);
//
//                                if (requestMessage.isCommand(ClientCommand.SUBSCRIBE)) {
//                                    final String subscriptionId = HexUtil.toHexString(Difficulty.BASE_DIFFICULTY.encode()) + "00000000"; // _createRandomBytes(8);
//                                    // final ByteArray extraNonce1 = _createRandomBytes(4);
//                                    Logger.log("ExtraNonce1: "+ extraNonce);
//                                    final Integer extraNonce2ByteCount = 4;
//
//                                    final Json resultJson = new Json(true); {
//                                        final Json setDifficulty = new Json(true); {
//                                            setDifficulty.add("mining.set_difficulty");
//                                            // setDifficulty.add(subscriptionId);
//                                            setDifficulty.add(HexUtil.toHexString(Difficulty.BASE_DIFFICULTY.encode()) + "00000000");
//                                        }
//
//                                        final Json notify = new Json(true); {
//                                            notify.add("mining.notify");
//                                            notify.add(subscriptionId);
//                                        }
//
//                                        final Json subscriptions = new Json(true); {
//                                            subscriptions.add(setDifficulty);
//                                            subscriptions.add(notify);
//                                        }
//
//                                        resultJson.add(subscriptions);
//                                        resultJson.add(extraNonce);
//                                        resultJson.add(extraNonce2ByteCount);
//                                    }
//
//                                    final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
//                                    responseMessage.setResult(resultJson);
//                                    socketConnection.write(responseMessage.toString());
//                                }
//                                else if (requestMessage.isCommand(ClientCommand.AUTHORIZE)) {
//                                    {
//                                        final ResponseMessage responseMessage = new ResponseMessage(requestMessage.getId());
//                                        responseMessage.setResult(ResponseMessage.RESULT_TRUE);
//                                        socketConnection.write(responseMessage.toString());
//                                    }
//
//                                    {
//                                        // {"method":"mining.notify","id":0,"params": [
//                                        //  jobId:                      "1FE8",
//                                        //  previousBlockHash:          "CB81CDB5ED9DFDCC7973B4D9A0280CAFA6FF6698011AA2F60000000000000000",
//                                        //  coinbaseTransactionPart1:   "01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF5D0368F9071A2F5669614254432F4D696E656420627920757365726E616D652F2CFABE6D6D52D42620289BA8F85B89CAA13794AFBD611B2DB2746B4340070B6C6135E81B00010000000000000010E81F800C93E4D840",
//                                        //  coinbaseTransactionPart2:   "FFFFFFFF01C8CE864A000000001976A914F1C075A01882AE0972F95D3A4177C86C852B7D9188AC00000000",
//                                        //  merkleTree:                 ["5C62C5F5C0C5A456C489203E3CA0427991BD4647A0334142E0E5F78BC987682B","2077E1D8CB57A4C6EC8DDF6D6E8470A04FF7F98030BE721A5B83B21ED939E76D","F051F497B0A178ED509A7D3335CD0528A0B28ACFD5094D07EBBB0629B606D759","8413CBE0D9903F5E5499359BD85A9DB986DA614CE4C6A228C53969573FC8A748","07381797C454A3D76DF6D7E6E601A9682C26B341F7E8F9E3E8E1549784074A33","FE0F47DCD89325926FA507045F0CDB2C9A124719B7FC15EA93DB120BC7C29592","AF6EEE880810751C48B94118FBD7DEE1904B673584BC6C26A9C36134E42C968A"],
//                                        //  blockVersion:               "20000000",
//                                        //  difficulty:                 "1802D306",
//                                        //  timestamp:                  "5AB454FB",
//                                        //  stopOtherJobs:              true
//                                        // ]}
//
//                                        // Job ID. This is included when miners submit a results so work can be matched with proper transactions.
//                                        // Hash of previous block. Used to build the header.
//                                        // Generation transaction (part 1). The miner inserts ExtraNonce1 and ExtraNonce2 after this section of the transaction data.
//                                        // Generation transaction (part 2). The miner appends this after the first part of the transaction data and the two ExtraNonce values.
//                                        // List of merkle branches. The generation transaction is hashed against the merkle branches to build the final merkle root.
//                                        // Bitcoin block version. Used in the block header.
//                                        // nBits. The encoded network difficulty. Used in the block header.
//                                        // nTime. The current time. nTime rolling should be supported, but should not increase faster than actual time.
//                                        // Clean Jobs. If true, miners should abort their current work and immediately use the new job. If false, they can still use the current job, but should move to the new one after exhausting the current nonce range.
//
//                                        final RequestMessage mineBlockMessage = new RequestMessage(RequestMessage.ServerCommand.NOTIFY.getValue());
//                                        {
//                                            final TransactionDeflater transactionDeflater = new TransactionDeflater();
//
//                                            Logger.log(privateKey);
//                                            Logger.log(Address.fromPrivateKey(privateKey).toBase58CheckEncoded());
//
//                                            final MutableBlock prototypeBlock = new MutableBlock();
//                                            prototypeBlock.setVersion(BlockHeader.VERSION);
//                                            prototypeBlock.setPreviousBlockHash(BlockHeader.GENESIS_BLOCK_HEADER_HASH);
//                                            prototypeBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
//                                            prototypeBlock.setTimestamp(System.currentTimeMillis() / 1000L);
//                                            prototypeBlock.addTransaction(Transaction.createCoinbaseTransaction("/Mined via " + Constants.USER_AGENT + "/", Address.fromPrivateKey(privateKey), 50 * Transaction.SATOSHIS_PER_BITCOIN));
//                                            prototypeBlock.setNonce(0L);
//
//                                            final ByteArray taskId = MutableByteArray.wrap(HexUtil.hexStringToByteArray("0000"));
//                                            final FragmentedBytes coinbaseTransactionParts = transactionDeflater.fragmentTransaction(prototypeBlock.getCoinbaseTransaction());
//
//                                            final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
//                                            final BlockHeaderDeflater.BlockHeaderByteData blockHeaderByteData = blockHeaderDeflater.toByteData(prototypeBlock);
//
//                                            final Json parametersJson = new Json(true);
//                                            parametersJson.add(taskId);
//                                            parametersJson.add(HexUtil.toHexString(ByteUtil.reverseEndian(blockHeaderByteData.previousBlockHash)));
//                                            parametersJson.add(HexUtil.toHexString(coinbaseTransactionParts.headBytes));
//                                            parametersJson.add(HexUtil.toHexString(coinbaseTransactionParts.tailBytes));
//
//                                            { // Create the partialMerkleTree Json...
//                                                final Json partialMerkleTreeJson = new Json(true);
//                                                final List<Hash> partialMerkleTree = prototypeBlock.getPartialMerkleTree(0);
//                                                for (final Hash hash : partialMerkleTree) {
//                                                    partialMerkleTreeJson.add(hash.toString());
//                                                }
//                                                parametersJson.add(partialMerkleTreeJson);
//                                            }
//
//                                            parametersJson.add(HexUtil.toHexString(blockHeaderByteData.version));
//                                            parametersJson.add(HexUtil.toHexString(blockHeaderByteData.difficulty));
//                                            parametersJson.add(HexUtil.toHexString(blockHeaderByteData.timestamp));
//                                            parametersJson.add(true);
//
//                                            mineBlockMessage.setParameters(parametersJson);
//
//                                            Logger.log("Block: "+ HexUtil.toHexString(blockHeaderDeflater.toBytes(prototypeBlock)));
//                                            Logger.log("Coinbase: "+ HexUtil.toHexString(transactionDeflater.toBytes(prototypeBlock.getCoinbaseTransaction())));
//                                            Logger.log("Coinbase0: "+ HexUtil.toHexString(coinbaseTransactionParts.headBytes));
//                                            Logger.log("Coinbase1: "+ HexUtil.toHexString(coinbaseTransactionParts.tailBytes));
//                                        }
//
//                                        Logger.log("Sent: "+ mineBlockMessage.toString());
//
//                                        socketConnection.write(mineBlockMessage.toString());
//                                    }
//                                }
//                                else if (requestMessage.isCommand(ClientCommand.SUBMIT)) {
//                                    // mining.submit("username", "job id", "ExtraNonce2", "nTime", "nOnce")
//
//                                    final Json messageParameters = requestMessage.getParameters();
//                                    final String workerName = messageParameters.getString(0);
//                                    final ByteArray taskId = MutableByteArray.wrap(HexUtil.hexStringToByteArray(messageParameters.getString(1)));
//                                    final byte[] nonce = HexUtil.hexStringToByteArray(messageParameters.getString(4));
//                                    final byte[] extraNonce2 = HexUtil.hexStringToByteArray(messageParameters.getString(2));
//                                    final byte[] timestamp = HexUtil.hexStringToByteArray(messageParameters.getString(3));
//
//                                    final MutableBlock prototypeBlock = new MutableBlock();
//                                    prototypeBlock.setVersion(BlockHeader.VERSION);
//                                    prototypeBlock.setPreviousBlockHash(BlockHeader.GENESIS_BLOCK_HEADER_HASH);
//                                    prototypeBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
//                                    prototypeBlock.setTimestamp(ByteUtil.bytesToLong(timestamp));
//
//                                    final Transaction coinbaseTransaction;
//                                    {
//                                        final MutableTransaction mutableTransaction = new MutableTransaction(Transaction.createCoinbaseTransaction("/Mined via " + Constants.USER_AGENT + "/", Address.fromPrivateKey(privateKey), 50 * Transaction.SATOSHIS_PER_BITCOIN));
//                                        final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput(mutableTransaction.getTransactionInputs().get(0));
//                                        final UnlockingScript unlockingScript = mutableTransactionInput.getUnlockingScript();
//                                        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
//                                        byteArrayBuilder.appendBytes(unlockingScript.getBytes());
//                                        byteArrayBuilder.appendBytes(extraNonce);
//                                        byteArrayBuilder.appendBytes(extraNonce2);
//                                        mutableTransactionInput.setUnlockingScript(new ImmutableUnlockingScript(byteArrayBuilder.build()));
//                                        mutableTransaction.setTransactionInput(0, mutableTransactionInput);
//                                        coinbaseTransaction = mutableTransaction;
//                                    }
//                                    prototypeBlock.addTransaction(coinbaseTransaction);
//                                    prototypeBlock.setNonce(com.softwareverde.bitcoin.util.ByteUtil.bytesToLong(nonce));
//
//                                    final Hash hash = prototypeBlock.getHash();
//                                    Logger.log(hash);
//                                    if (prototypeBlock.isValid()) {
//                                        final BlockDeflater blockDeflater = new BlockDeflater();
//                                        Logger.log("Valid Block: "+ HexUtil.toHexString(blockDeflater.toBytes(prototypeBlock).getBytes()));
//                                    }
//                                }
//                            }
//                        }
//
//                        { // Handle Response Messages...
//                            final ResponseMessage responseMessage = ResponseMessage.parse(message);
//                            if (responseMessage != null) { System.out.println(responseMessage); }
//                        }
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
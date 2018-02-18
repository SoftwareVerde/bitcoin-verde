package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.BlockTracker;
import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.ping.PingMessage;
import com.softwareverde.bitcoin.server.message.type.node.pong.PongMessage;
import com.softwareverde.bitcoin.server.message.type.query.block.GetBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.SynchronizeVersionMessage;
import com.softwareverde.bitcoin.server.socket.ConnectionManager;
import com.softwareverde.bitcoin.server.socket.ip.Ipv4;
import com.softwareverde.bitcoin.util.BitcoinUtil;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

public class Main {
    protected static class DataHashManager {
        private Integer _index = 0;
        private final List<DataHash> _dataHashes = new ArrayList<DataHash>();

        public void setDataHashes(final List<DataHash> dataHashes) {
            _index = 0;
            _dataHashes.clear();
            _dataHashes.addAll(dataHashes);
        }

        public DataHash getNextDataHash() {
            final DataHash dataHash = _dataHashes.get(_index);
            _index += 1;
            return dataHash;
        }

        public Boolean hasNextDataHash() {
            return (_index < _dataHashes.size());
        }
    }
    protected final DataHashManager _dataHashManager = new DataHashManager();

    protected final Configuration _configuration;
    protected final Environment _environment;

    protected void _exitFailure() {
        System.exit(1);
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected void _printUsage() {
        _printError("Usage: java -jar " + System.getProperty("java.class.path") + " <configuration-file>");
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            _exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected void _printMemoryUsage() {
        final Runtime runtime = Runtime.getRuntime();
        final Long maxMemory = runtime.maxMemory();
        final Long freeMemory = runtime.freeMemory();
        final Long reservedMemory = runtime.totalMemory();
        final Long currentMemoryUsage = reservedMemory - freeMemory;

        final Long toMegabytes = 1048576L;
        System.out.print("\33[1A\33[2K");
        System.out.println((currentMemoryUsage/toMegabytes) +"mb / "+ (maxMemory/toMegabytes) +"mb ("+ String.format("%.2f", (currentMemoryUsage.floatValue() / maxMemory.floatValue() * 100.0F)) +"%)");
    }

    protected void _checkForDeadlockedThreads() {
        final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        final long[] threadIds = bean.findDeadlockedThreads(); // Returns null if no threads are deadlocked.

        if (threadIds != null) {
            final ThreadInfo[] threadInfo = bean.getThreadInfo(threadIds);

            for (final ThreadInfo info : threadInfo) {
                final StackTraceElement[] stack = info.getStackTrace();
                for (final StackTraceElement stackTraceElement : stack) {
                    System.out.println(stackTraceElement);
                }
            }
        }
    }

    protected void _requestNextBlock(final ConnectionManager connectionManager) {
        final RequestDataMessage requestDataMessage = new RequestDataMessage();
        requestDataMessage.addInventoryItem(_dataHashManager.getNextDataHash());
        connectionManager.queueMessage(requestDataMessage);
    }

    public Main(final String[] commandLineArguments) {
        if (commandLineArguments.length != 1) {
            _printUsage();
            _exitFailure();
        }

        final String configurationFilename = commandLineArguments[0];

        _configuration = _loadConfigurationFile(configurationFilename);
        _environment = new Environment();

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
    }

    public void loop() {
        System.out.println("[Server Online]");

        final String host = "btc.softwareverde.com";
        final Integer port = 8333;

        final ConnectionManager connectionManager = new ConnectionManager(host, port);

        connectionManager.setMessageReceivedCallback(new ConnectionManager.MessageReceivedCallback() {
            @Override
            public void onMessageReceived(final ProtocolMessage message) {
                switch (message.getCommand()) {

                    case PING: {
                        final PingMessage pingMessage = (PingMessage) message;
                        final PongMessage pongMessage = new PongMessage();
                        pongMessage.setNonce(pingMessage.getNonce());
                        connectionManager.queueMessage(pongMessage);
                    } break;

                    case ACKNOWLEDGE_VERSION: {
                        final PingMessage pingMessage = new PingMessage();
                        connectionManager.queueMessage(pingMessage);

                        final GetBlocksMessage getBlocksMessage = new GetBlocksMessage();
                        connectionManager.queueMessage(getBlocksMessage);
                    } break;

                    case NODE_ADDRESSES: {
                        final NodeIpAddressMessage nodeIpAddressMessage = (NodeIpAddressMessage) message;
                        for (final NodeIpAddress nodeIpAddress : nodeIpAddressMessage.getNodeIpAddresses()) {
                            System.out.println("Network Address: "+ BitcoinUtil.toHexString(nodeIpAddress.getBytesWithTimestamp()));
                        }
                    } break;

                    case REJECT: {
                        final ErrorMessage errorMessage = (ErrorMessage) message;
                        final ErrorMessage.RejectCode rejectCode = errorMessage.getRejectCode();
                        System.out.println("RECEIVED REJECT:"+ rejectCode.getRejectMessageType().getValue() +" "+ BitcoinUtil.toHexString(new byte[] { rejectCode.getCode() }) +" "+ errorMessage.getRejectDescription() +" "+ BitcoinUtil.toHexString(errorMessage.getExtraData()));
                    } break;

                    case INVENTORY: {
                        final QueryResponseMessage queryResponseMessage = (QueryResponseMessage) message;
                        synchronized (_dataHashManager) {
                            _dataHashManager.setDataHashes(queryResponseMessage.getDataHashes());
                        }

                        if (_dataHashManager.hasNextDataHash()) {
                            _requestNextBlock(connectionManager);
                        }
                    } break;

                    case BLOCK: {
                        final BlockTracker blockTracker = BlockTracker.getInstance();

                        final BlockMessage blockMessage = (BlockMessage) message;
                        final Block block = blockMessage.getBlock();

                        final Boolean blockIsValid = blockTracker.validateBlock(block);
                        System.out.println("BLOCK: "+ BitcoinUtil.toHexString(block.calculateSha256Hash()) + " | "+ (blockIsValid ? "VALIDATED" : "INVALID"));

                        if (blockIsValid) {
                            blockTracker.addBlock(block);

                            if (_dataHashManager.hasNextDataHash()) {
                                _requestNextBlock(connectionManager);
                            }
                        }
                    }
                }

                // System.out.println("RECEIVED "+ message.getCommand() +": 0x"+ BitcoinUtil.toHexString(message.getHeaderBytes()));
            }
        });

        connectionManager.setOnConnectCallback(new Runnable() {
            @Override
            public void run() {
                final SynchronizeVersionMessage synchronizeVersionMessage = new SynchronizeVersionMessage();
                { // Set Remote NodeIpAddress...
                    final NodeIpAddress remoteNodeIpAddress = new NodeIpAddress();
                    remoteNodeIpAddress.setIp(Ipv4.parse(connectionManager.getRemoteIp()));
                    remoteNodeIpAddress.setPort(port);
                    remoteNodeIpAddress.setNodeFeatures(new NodeFeatures());
                    synchronizeVersionMessage.setRemoteAddress(remoteNodeIpAddress);
                }
                connectionManager.queueMessage(synchronizeVersionMessage);
            }
        });

        connectionManager.setOnDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                System.out.println("Socket disconnected.");
            }
        });

        connectionManager.startConnectionThread();

        while (true) {
            try { Thread.sleep(500); } catch (final Exception e) { }

            if ((Math.random() * 777) % 1000 < 10) {
                System.gc();
            }

            // _printMemoryUsage();
            // _checkForDeadlockedThreads();
        }
    }

    public static void main(final String[] commandLineArguments) {
        final Main application = new Main(commandLineArguments);
        application.loop();
    }
}
package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.socket.SocketConnectionManager;
import com.softwareverde.bitcoin.server.socket.message.NodeFeatures;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeaderParser;
import com.softwareverde.bitcoin.server.socket.message.address.AddressMessage;
import com.softwareverde.bitcoin.server.socket.message.ping.PingMessage;
import com.softwareverde.bitcoin.server.socket.message.pong.PongMessage;
import com.softwareverde.bitcoin.server.socket.message.version.synchronize.SynchronizeVersionMessage;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.ip.Ipv4;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class Main {
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

        final SocketConnectionManager socketConnectionManager = new SocketConnectionManager(host, port);

        socketConnectionManager.setMessageReceivedCallback(new SocketConnectionManager.MessageReceivedCallback() {
            @Override
            public void onMessageReceived(final ProtocolMessage message) {
                switch (message.getCommand()) {

                    case PING: {
                        final PingMessage pingMessage = (PingMessage) message;
                        final PongMessage pongMessage = new PongMessage();
                        pongMessage.setNonce(pingMessage.getNonce());
                        socketConnectionManager.queueMessage(pongMessage);
                    } break;

                    case ACKNOWLEDGE_VERSION: {
                        final PingMessage pingMessage = new PingMessage();
                        socketConnectionManager.queueMessage(pingMessage);
                    } break;

                    case ADDRESS: {
                        final AddressMessage addressMessage = (AddressMessage) message;
                        for (final NetworkAddress networkAddress : addressMessage.getNetworkAddresses()) {
                            System.out.println("Network Address: "+ BitcoinUtil.toHexString(networkAddress.getBytesWithTimestamp()));
                        }
                    } break;
                }

                // System.out.println("RECEIVED "+ message.getCommand() +": 0x"+ BitcoinUtil.toHexString(message.getHeaderBytes()));
            }
        });

        socketConnectionManager.setOnConnectCallback(new Runnable() {
            @Override
            public void run() {
                final SynchronizeVersionMessage synchronizeVersionMessage = new SynchronizeVersionMessage();
                { // Set Remote NetworkAddress...
                    final NetworkAddress remoteNetworkAddress = new NetworkAddress();
                    remoteNetworkAddress.setIp(Ipv4.parse(socketConnectionManager.getRemoteIp()));
                    remoteNetworkAddress.setPort(port);
                    remoteNetworkAddress.setNodeFeatures(new NodeFeatures());
                    synchronizeVersionMessage.setRemoteAddress(remoteNetworkAddress);
                }
                socketConnectionManager.queueMessage(synchronizeVersionMessage);
            }
        });

        socketConnectionManager.setOnDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                System.out.println("Socket disconnected.");
            }
        });

        socketConnectionManager.startConnectionThread();

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
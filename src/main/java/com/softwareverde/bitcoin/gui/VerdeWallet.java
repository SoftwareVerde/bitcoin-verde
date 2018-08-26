package com.softwareverde.bitcoin.gui;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.File;
import java.net.Socket;

public class VerdeWallet extends Application {
    protected final BorderPane _rootPane;
    protected final Label _statusBar;
    protected final TabPane _tabPane;
    protected final Tab _transactionHistoryTab;

    protected Double _width = 720D / 2D;
    protected Double _height = 960D / 2D;

    protected Runnable _statusUpdateRunnable = new Runnable() {
        protected JsonSocket _jsonSocket;

        protected Float _transactionsPerSecond = 0F;
        protected Float _blocksPerSecond = 0F;
        protected String _blockDate = "Querying";
        protected final Integer _maxSleepCount = 4;
        protected Integer _sleepCount = 0;

        @Override
        public void run() {
            while (! Thread.interrupted()) {
                if (_jsonSocket == null) {
                    try {
                        final Configuration.WalletProperties walletProperties = _configuration.getWalletProperties();
                        final String host = walletProperties.getBitcoinRpcUrl();
                        final Integer port = walletProperties.getBitcoinRpcPort();

                        final Socket tcpSocket = new Socket(host, port);
                        _jsonSocket = new JsonSocket(tcpSocket);
                        _jsonSocket.beginListening();
                    }
                    catch (final Exception exception) { }
                }

                if ((_jsonSocket != null) && (_sleepCount % _maxSleepCount == 0)) {
                    final JsonProtocolMessage responseMessage = _jsonSocket.popMessage();
                    if (responseMessage != null) {
                        final Json responseJson = responseMessage.getMessage();
                        final Json statisticsJson = responseJson.get("statistics");
                        _blockDate = statisticsJson.getString("blockDate");
                        _blocksPerSecond = statisticsJson.getFloat("blocksPerSecond");
                        _transactionsPerSecond = statisticsJson.getFloat("transactionsPerSecond");
                    }

                    final Json queryStatusJson = new Json();
                    queryStatusJson.put("method", "GET");
                    queryStatusJson.put("query", "STATUS");

                    _jsonSocket.write(new JsonProtocolMessage(queryStatusJson));
                }

                final String ellipses;
                {
                    final StringBuilder stringBuilder = new StringBuilder();
                    for (int i = 0; i < _maxSleepCount; ++i) {
                        stringBuilder.append(((i < _sleepCount) ? "." : " "));
                    }
                    ellipses = stringBuilder.toString();
                }

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        final String blocksPerSecondMessage;
                        if (_blocksPerSecond >= 1.0F || _blocksPerSecond <= 0.0001) {
                            blocksPerSecondMessage = String.format("%.2f", _blocksPerSecond) + " blocks/sec";
                        }
                        else {
                            blocksPerSecondMessage = String.format("%.2f", (1.0F / _blocksPerSecond)) + " seconds/block";
                        }

                        final String transactionsPerSecondMessage = String.format("%.2f", _transactionsPerSecond) + " transactions/sec";

                        _statusBar.setText("Synchronization Date: " + _blockDate + " " + ellipses + "\n" + blocksPerSecondMessage + " | " + transactionsPerSecondMessage);
                    }
                });

                try {
                    Thread.sleep(500);
                }
                catch (final InterruptedException exception) { break; }

                _sleepCount = ((_sleepCount + 1) % _maxSleepCount);
            }
        }
    };
    protected Thread _statusThread;

    protected Configuration _configuration;

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.error("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    public VerdeWallet() {
        _rootPane = new BorderPane();
        _rootPane.setId("root");

        _tabPane = new TabPane();

        _transactionHistoryTab = new Tab();
        _transactionHistoryTab.setText("Transaction History");

        _statusBar = new Label("Synchronizing...");
        _statusBar.getStyleClass().add("status-bar");
    }

    @Override
    public void init() {
        final java.util.List<String> arguments = this.getParameters().getRaw();

        if (arguments.size() != 2) {
            BitcoinUtil.exitFailure();
        }

        _configuration = _loadConfigurationFile(arguments.get(1));
    }

    @Override
    public void start(final Stage primaryStage) {
        final MysqlDatabaseConnectionFactory databaseConnectionFactory;
        {
            final DatabaseProperties databaseProperties = _configuration.getDatabaseProperties();
            final String connectionUrl = MysqlDatabaseConnectionFactory.createConnectionString(databaseProperties.getConnectionUrl(), databaseProperties.getPort(), databaseProperties.getSchema());
            final String username = databaseProperties.getUsername();
            final String password = databaseProperties.getPassword();

            databaseConnectionFactory = new MysqlDatabaseConnectionFactory(connectionUrl, username, password);
        }

        final Scene scene = new Scene(_rootPane, _width, _height, Color.WHITE);
        primaryStage.setMinWidth(_width);
        primaryStage.setMinHeight(_height);
        primaryStage.sizeToScene();

        _rootPane.setMaxWidth(scene.getWidth());
        _rootPane.setMinHeight(scene.getHeight());

        { // CSS
            final String cssFilename = this.getClass().getResource("/css/main.css").toExternalForm();
            scene.getStylesheets().add(cssFilename);
        }

        _tabPane.setMinWidth(_rootPane.getMinWidth());
        _tabPane.prefHeightProperty().bind(_rootPane.heightProperty());
        _tabPane.prefWidthProperty().bind(_rootPane.widthProperty());
        _tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        final ObservableList<Tab> tabs = _tabPane.getTabs();

        {
            final TransactionsPane transactionsPane = new TransactionsPane(databaseConnectionFactory);
            _transactionHistoryTab.setContent(transactionsPane);
            tabs.add(_transactionHistoryTab);
        }

        _rootPane.setCenter(_tabPane);
        _rootPane.setBottom(_statusBar);

        primaryStage.setScene(scene);
        primaryStage.setTitle("Bitcoin Verde");
        primaryStage.show();

        _statusThread = new Thread(_statusUpdateRunnable);
        _statusThread.start();
    }

    @Override
    public void stop() {
        _statusThread.interrupt();
        try { _statusThread.join(); } catch (final Exception exception) { }
        Logger.shutdown();
    }

}

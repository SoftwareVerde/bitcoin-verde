package com.softwareverde.bitcoin.gui;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.io.Logger;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.File;

public class VerdeWallet extends Application {
    protected TabPane _tabPane;

    protected Tab _transactionHistoryTab;

    protected Double _width = 720D / 2D;
    protected Double _height = 960D / 2D;

    protected Configuration _configuration;

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.error("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    public VerdeWallet() { }

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

        final Pane root = new Pane();
        root.setId("root");
        final ObservableList<Node> children = root.getChildren();

        final Scene scene = new Scene(root, _width, _height, Color.WHITE);
        primaryStage.setMinWidth(_width);
        primaryStage.setMinHeight(_height);
        primaryStage.sizeToScene();

        { // CSS
            final String cssFilename = this.getClass().getResource("/css/main.css").toExternalForm();
            scene.getStylesheets().add(cssFilename);
        }

        _tabPane = new TabPane();
        _tabPane.setMinWidth(scene.getWidth());
        _tabPane.setMinHeight(scene.getHeight());
        _tabPane.prefHeightProperty().bind(root.heightProperty());
        _tabPane.prefWidthProperty().bind(root.widthProperty());
        _tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        final ObservableList<Tab> tabs = _tabPane.getTabs();

        {
            final TransactionsPane transactionsPane = new TransactionsPane(databaseConnectionFactory);
            _transactionHistoryTab = new Tab();
            _transactionHistoryTab.setText("Transaction History");
            _transactionHistoryTab.setContent(transactionsPane);
            tabs.add(_transactionHistoryTab);
        }

        children.add(_tabPane);

        primaryStage.setScene(scene);
        primaryStage.setTitle("Bitcoin Verde");
        primaryStage.show();
    }

    @Override
    public void stop() { }

}

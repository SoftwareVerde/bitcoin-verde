package com.softwareverde.bitcoin.gui;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.HashMap;
import java.util.Map;

public class TransactionsPane extends GridPane {

//    private final Runnable _onSaveConfigurationCallback;
//    private final KeyManager _keyManager;
//    private final Configuration _configuration;
//    private ManageKeysContainer _manageKeysContainer;
//
//    public TransactionsPane(final KeyManager keyManager, final Configuration configuration, final Runnable onSaveConfigurationCallback) {
//        super();
//
//        _keyManager = keyManager;
//        _configuration = configuration;
//        _onSaveConfigurationCallback = onSaveConfigurationCallback;
//
//        setPadding(new Insets(10, 10, 10, 10));
//        setVgap(20);
//        setHgap(20);
//
//        _init();
//    }
//
//    private void _init() {
//        final ObservableList<Node> children = getChildren();
//        children.clear();
//
//        final String currentUsername =_configuration.getUserName();
//        String currentServerHost = _configuration.parseHostString();
//        String currentServerPort = _configuration.parsePort().toString();
//
//        final TextField userNameField = new TextField();
//        userNameField.setPrefColumnCount(30);
//        userNameField.setText(currentUsername);
//        final LabeledControl userNameControl = new LabeledControl("User Name", userNameField);
//        children.add(userNameControl);
//
//        final TextField serverHostField = new TextField();
//        serverHostField.setPrefColumnCount(30);
//        serverHostField.setText(currentServerHost);
//        final LabeledControl serverHostControl = new LabeledControl("Server Host", serverHostField);
//        children.add(serverHostControl);
//
//        final TextField serverPortField = new TextField();
//        serverPortField.setPrefColumnCount(30);
//        serverPortField.setText(currentServerPort);
//        final LabeledControl serverPortControl = new LabeledControl("Server Port", serverPortField);
//        children.add(serverPortControl);
//
//        final Button saveButton = new Button("Save");
//        children.add(saveButton);
//
//        _manageKeysContainer = new ManageKeysContainer(_keyManager, _configuration);
//        children.add(_manageKeysContainer);
//        GridPane.setConstraints(_manageKeysContainer, 0, 4);
//
//        setConstraints(userNameControl, 0, 0);
//        setConstraints(serverHostControl, 0, 1);
//        setConstraints(serverPortControl, 0, 2);
//        setConstraints(saveButton, 0, 3);
//
//        saveButton.setOnAction((event) -> {
//            final HashMap<String, String> configurationMap = new HashMap<>();
//            final String existingUserName = _configuration.getUserName();
//            final String userName = userNameField.getText();
//            final String serverHost = serverHostField.getText();
//            final String serverPort = serverPortField.getText();
//
//            configurationMap.put("user.name", userName);
//            configurationMap.put("server.host", serverHost);
//            configurationMap.put("server.port", serverPort);
//
//            _setConfig(configurationMap);
//            _manageKeysContainer.clearManageKeyResultsText();
//
//            boolean showSuccessAlert = true;
//            if (! userName.equals(existingUserName)) {
//                final Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
//                infoAlert.setHeaderText("Configuration Change");
//                infoAlert.setContentText("The application will attempt to retrieve keys for the new user identity that was provided.\n\n" +
//                        "This will action will delete the existing keys for the current user on this device. These keys can be reloaded from the server.");
//                infoAlert.showAndWait();
//
//                if (PgpKeys.doLocalPgpKeysExist(_configuration.getConfigurationDirectory())) {
//                    _keyManager.clearKeys();
//                }
//
//                final boolean isConfigurationValid = DocChainApplication.checkAndInitUserConfiguration();
//
//                if (! isConfigurationValid) {
//                    _manageKeysContainer.setChangePasswordButtonIsVisible(false);
//                    showSuccessAlert = false;
//                }
//            }
//
//            _saveConfigurationChanges(showSuccessAlert);
//        });
//    }
//
//    public ManageKeysContainer getManageKeysContainer() {
//        return this._manageKeysContainer;
//    }
//
//    public static void showInvalidConfigurationError() {
//        JavaFxUtil.runOnUiThread(() -> {
//            final Alert errorAlert = new Alert(Alert.AlertType.ERROR);
//            errorAlert.setHeaderText("Configuration Error");
//            errorAlert.setContentText("User configuration is not valid. Please specify the following fields and save your changes to continue.");
//            errorAlert.showAndWait();
//        });
//    }
//
//    public static void showLoadKeysError(final Exception exception) {
//        _logger.error("Unable to load keys", exception);
//
//        JavaFxUtil.runOnUiThread(() -> {
//            final Alert alert = new Alert(Alert.AlertType.ERROR);
//            alert.setTitle("Unable to Load Keys");
//            alert.setContentText("Here's what went wrong: \n\n" + exception.getClass().getSimpleName() + ": " + exception.getMessage());
//            alert.show();
//        });
//    }
//
//    private void _triggerOnSaveConfigurationCallback() {
//        _onSaveConfigurationCallback.run();
//    }
//
//    private void _setConfig(final HashMap<String, String> configurationMap) {
//        for (Map.Entry<String, String> property : configurationMap.entrySet()) {
//            final String propertyName = property.getKey();
//            final String propertyValue = property.getValue();
//
//            _configuration.setProperty(propertyName, propertyValue);
//        }
//    }
//
//    private void _saveConfigurationChanges(final boolean showSuccessAlert) {
//        _configuration.saveConfiguration();
//        if (PgpKeys.doLocalPgpKeysExist(_configuration.getConfigurationDirectory())) {
//            _triggerOnSaveConfigurationCallback();
//            _manageKeysContainer.setChangePasswordButtonIsVisible(true);
//        }
//
//        if (showSuccessAlert) {
//            final Alert alert = new Alert(Alert.AlertType.SUCCESS);
//            alert.setHeaderText("Success");
//            alert.setContentText("Configuration saved.");
//            alert.show();
//        }
//    }
}

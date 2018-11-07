package com.softwareverde.bitcoin.gui;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.Util;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;

public class TransactionsPane extends GridPane {

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    protected final LabeledNode<AutoCompleteTextField> _addressInputNode;
    protected final LabeledNode<Label> _addressBalanceNode;
    protected final ListView<Label> _transactionListView;

    protected LabeledNode<TextField> _createTextField(final String label, final String value) {
        final Integer preferredColumnCount = 30;

        final TextField textField = new TextField(Util.coalesce(value));
        textField.setPrefColumnCount(preferredColumnCount);

        return new LabeledNode<TextField>(label, textField);
    }

    public TransactionsPane(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        super();

        _databaseConnectionFactory = databaseConnectionFactory;
        final DatabaseManagerCache databaseManagerCache = new DisabledDatabaseManagerCache();

        this.setPadding(new Insets(10, 10, 10, 10));
        this.setVgap(20);
        this.setHgap(20);

        final ObservableList<Node> children = this.getChildren();
        this.getChildren().clear();

        { // Address TextField...
            final AutoCompleteTextField textField = new AutoCompleteTextField();
            textField.setPrefColumnCount(34);
            _addressInputNode = new LabeledNode<AutoCompleteTextField>("Address", textField);
            children.add(_addressInputNode);
            GridPane.setConstraints(_addressInputNode, 0, 0);
        }

        { // Address Balance Text...
            final Label addressBalanceText = new Label("");
            _addressBalanceNode = new LabeledNode<Label>("Balance: ", addressBalanceText);
            children.add(_addressBalanceNode);
            GridPane.setConstraints(_addressBalanceNode, 0, 1);
        }

        { // Assigned onChange listener to TextField...
            final StringProperty addressStringProperty;
            {
                final AutoCompleteTextField autoCompleteTextField = _addressInputNode.getNode();
                addressStringProperty = autoCompleteTextField.textProperty();
            }

            addressStringProperty.addListener(new ChangeListener<String>() {
                @Override
                public void changed(final ObservableValue<? extends String> observable, final String oldValue, final String newValue) {
                    final String sanitizedAddressValue = newValue.replaceAll("[^A-Za-z0-9]", "");
                    try (MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                        final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(databaseConnection, databaseManagerCache);

                        final Boolean addressIsValid;
                        final AddressId addressId;
                        {
                            final String addressString = newValue;
                            final AddressInflater addressInflater = new AddressInflater();
                            final Address address = addressInflater.fromBase58Check(addressString);
                            addressIsValid = (address != null);
                            addressId = (addressIsValid ? addressDatabaseManager.getAddressId(addressString) : null);
                        }

                        { // Populate Address autoComplete options...
                            final java.util.List<Row> rows = databaseConnection.query(
                                new Query("SELECT * FROM addresses WHERE address LIKE ? LIMIT 10")
                                    .setParameter(sanitizedAddressValue + "%")
                            );

                            final SortedSet<String> addresses = new TreeSet<String>();
                            for (final Row row : rows) {
                                final String address = row.getString("address");
                                addresses.add(address);
                            }

                            _addressInputNode.getNode().setEntries(addresses);
                        }

                        { // Display Address balance...
                            final Label addressBalanceText = _addressBalanceNode.getNode();
                            addressBalanceText.setText("");

                            if (addressIsValid) {
                                if (addressId != null) {
                                    final BigInteger addressBalance = addressDatabaseManager.getAddressBalance(addressId);
                                    addressBalanceText.setText(StringUtil.formatNumberString(addressBalance.longValue()) + " Satoshis");
                                }
                            }
                        }

                        { // Display Address transactions history...
                            final ObservableList<Label> transactionListItems = _transactionListView.getItems();
                            transactionListItems.clear();

                            if (addressIsValid) {
                                if (addressId != null) {
                                    final List<AddressDatabaseManager.SpendableTransactionOutput> spendableTransactionOutputs = addressDatabaseManager.getSpendableTransactionOutputs(addressId);

                                    final java.util.List<Label> addressTransactions = new ArrayList<Label>(spendableTransactionOutputs.getSize());

                                    final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, databaseManagerCache);
                                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, databaseManagerCache);
                                    final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, databaseManagerCache);
                                    final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

                                    for (AddressDatabaseManager.SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
                                        final TransactionId transactionId = spendableTransactionOutput.getTransactionId();

                                        final Sha256Hash transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);
                                        final String transactionHashString = transactionHash.toString();

                                        final String transactionDateString;
                                        {
                                            final BlockId blockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);
                                            if (blockId == null) {
                                                transactionDateString = "UNCONFIRMED";
                                            }
                                            else {
                                                final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                                                final Long timestampInSeconds = blockHeader.getTimestamp();
                                                transactionDateString = DateUtil.timestampToDatetimeString(timestampInSeconds * 1000L);
                                            }
                                        }

                                        final StringBuilder stringBuilder = new StringBuilder();
                                        stringBuilder.append(transactionDateString);
                                        stringBuilder.append(" - ");
                                        stringBuilder.append(transactionHashString.substring(0, 8));
                                        stringBuilder.append("...");
                                        stringBuilder.append(transactionHashString.substring(transactionHashString.length() - 8));
                                        stringBuilder.append(" -> ");
                                        stringBuilder.append(StringUtil.formatNumberString(spendableTransactionOutput.getAmount()));
                                        stringBuilder.append(" ");
                                        stringBuilder.append(spendableTransactionOutput.wasSpent() ? "(S)" : "(U)");

                                        final Label label = new Label(stringBuilder.toString());
                                        if (spendableTransactionOutput.wasSpent()) {
                                            label.getStyleClass().add("spent");
                                        }
                                        else {
                                            label.getStyleClass().add("unspent");
                                        }
                                        addressTransactions.add(label);
                                    }

                                    transactionListItems.addAll(addressTransactions);
                                }
                            }
                        }
                    }
                    catch (final DatabaseException exception) {
                        Logger.log(exception);
                    }
                }
            });
        }

        { // Transaction ListView...
            _transactionListView = new ListView<Label>();
            children.add(_transactionListView);
            GridPane.setConstraints(_transactionListView, 0, 2);
        }
    }
}

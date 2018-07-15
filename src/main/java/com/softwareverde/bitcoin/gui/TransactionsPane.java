package com.softwareverde.bitcoin.gui;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.SortedSet;
import java.util.TreeSet;

public class TransactionsPane extends GridPane {

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected LabeledControl<AutoCompleteTextField> _addressControl;

    protected LabeledControl<TextField> _createTextField(final String label, final String value) {
        final Integer preferredColumnCount = 30;

        final TextField textField = new TextField(Util.coalesce(value));
        textField.setPrefColumnCount(preferredColumnCount);

        return new LabeledControl<TextField>(label, textField);
    }

    public TransactionsPane(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        super();

        _databaseConnectionFactory = databaseConnectionFactory;

        this.setPadding(new Insets(10, 10, 10, 10));
        this.setVgap(20);
        this.setHgap(20);

        final ObservableList<Node> children = getChildren();
        children.clear();

        {
            final AutoCompleteTextField textField = new AutoCompleteTextField();
            textField.setPrefColumnCount(34);
            _addressControl = new LabeledControl<AutoCompleteTextField>("Address", textField);
            children.add(_addressControl);
        }

        final Button saveButton = new Button("Save");
        children.add(saveButton);

        GridPane.setConstraints(_addressControl, 0, 0);
        GridPane.setConstraints(saveButton, 0, 1);

        _addressControl.getControl().textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(final ObservableValue<? extends String> observable, final String oldValue, final String newValue) {
                final String sanitizedAddressValue = newValue.replaceAll("[^A-Za-z0-9]", "");
                try (MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final java.util.List<Row> rows = databaseConnection.query(
                        new Query("SELECT * FROM addresses WHERE address LIKE ? LIMIT 10")
                            .setParameter(sanitizedAddressValue + "%")
                    );

                    final SortedSet<String> addresses = new TreeSet<String>();
                    for (final Row row : rows) {
                        final String address = row.getString("address");
                        addresses.add(address);
                    }

                    _addressControl.getControl().setEntries(addresses);
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                }
            }
        });
    }
}

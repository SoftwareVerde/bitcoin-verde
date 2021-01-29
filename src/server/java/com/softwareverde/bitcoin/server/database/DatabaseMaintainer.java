package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class DatabaseMaintainer {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    protected static final List<String> TABLES;
    static {
        final ImmutableListBuilder<String> listBuilder = new ImmutableListBuilder<String>();
        listBuilder.add("properties");
        listBuilder.add("script_types");
        listBuilder.add("pending_blocks");
        listBuilder.add("invalid_blocks");
        listBuilder.add("pending_transactions");
        listBuilder.add("pending_transaction_data");
        listBuilder.add("pending_transactions_dependent_transactions");
        listBuilder.add("addresses");
        listBuilder.add("blocks");
        listBuilder.add("blockchain_segments");
        listBuilder.add("transactions");
        listBuilder.add("block_transactions");
        listBuilder.add("committed_unspent_transaction_outputs");
        listBuilder.add("unconfirmed_transactions");
        listBuilder.add("unconfirmed_transaction_outputs");
        listBuilder.add("unconfirmed_transaction_inputs");
        listBuilder.add("indexed_transaction_outputs");
        listBuilder.add("indexed_transaction_inputs");
        listBuilder.add("validated_slp_transactions");
        listBuilder.add("hosts");
        listBuilder.add("nodes");
        listBuilder.add("node_features");
        listBuilder.add("node_transactions_inventory");
        TABLES = listBuilder.build();
    }

    public DatabaseMaintainer(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public void analyzeTables() {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            for (final String tableName : TABLES) {
                databaseConnection.executeSql(new Query("ANALYZE TABLE " + tableName));
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}

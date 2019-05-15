package com.softwareverde.bitcoin.server.database;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.io.Logger;

public class DatabaseMaintainer {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    protected static final List<String> TABLES;
    static {
        final ImmutableListBuilder<String> listBuilder = new ImmutableListBuilder<>();
        listBuilder.add("pending_blocks");
        listBuilder.add("pending_block_data");
        listBuilder.add("pending_transactions");
        listBuilder.add("pending_transaction_data");
        listBuilder.add("pending_transactions_dependent_transactions");
        listBuilder.add("addresses");
        listBuilder.add("blocks");
        listBuilder.add("blockchain_segments");
        listBuilder.add("transactions");
        listBuilder.add("block_transactions");
        listBuilder.add("unconfirmed_transactions");
        listBuilder.add("transaction_outputs");
        listBuilder.add("unspent_transaction_outputs");
        listBuilder.add("transaction_inputs");
        listBuilder.add("script_types");
        listBuilder.add("locking_scripts");
        listBuilder.add("unlocking_scripts");
        listBuilder.add("address_processor_queue");
        listBuilder.add("hosts");
        listBuilder.add("nodes");
        listBuilder.add("node_features");
        listBuilder.add("node_blocks_inventory");
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
            Logger.log(exception);
        }
    }
}

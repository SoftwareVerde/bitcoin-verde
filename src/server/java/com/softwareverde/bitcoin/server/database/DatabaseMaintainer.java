package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.NanoTimer;

public class DatabaseMaintainer {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    protected static final List<String> TABLES;
    static {
        final ImmutableListBuilder<String> listBuilder = new ImmutableListBuilder<String>();
        listBuilder.add("block_transactions");
        listBuilder.add("blockchain_segments");
        listBuilder.add("blocks");
        listBuilder.add("committed_unspent_transaction_outputs");
        // listBuilder.add("head_block"); // View, not a Table.
        // listBuilder.add("head_block_header"); // View, not a Table.
        listBuilder.add("hosts");
        listBuilder.add("indexed_transaction_inputs");
        listBuilder.add("indexed_transaction_outputs");
        listBuilder.add("invalid_blocks");
        listBuilder.add("metadata");
        listBuilder.add("node_features");
        listBuilder.add("node_transactions_inventory");
        listBuilder.add("nodes");
        listBuilder.add("pending_transaction_data");
        listBuilder.add("pending_transactions");
        listBuilder.add("pending_transactions_dependent_transactions");
        listBuilder.add("properties");
        listBuilder.add("script_types");
        listBuilder.add("transactions");
        listBuilder.add("unconfirmed_transaction_inputs");
        listBuilder.add("unconfirmed_transaction_outputs");
        listBuilder.add("unconfirmed_transactions");
        listBuilder.add("validated_slp_transactions");
        TABLES = listBuilder.build();
    }

    public DatabaseMaintainer(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public void analyzeTables() {
        Logger.info("Analyzing " + TABLES.getCount() + " tables.");
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            for (final String tableName : TABLES) {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                databaseConnection.executeSql(new Query("ANALYZE TABLE " + tableName));
                nanoTimer.stop();
                Logger.trace("Analyzed table " + tableName + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}

package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.ReadUncommittedDatabaseConnectionFactoryWrapper;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.main.BitcoinVerdeDatabase;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.test.fake.FakeSynchronizationStatus;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.MysqlDatabaseInitializer;
import com.softwareverde.database.mysql.connection.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.test.database.MysqlTestDatabase;
import com.softwareverde.test.database.TestDatabase;
import com.softwareverde.util.Container;
import com.softwareverde.util.ReflectionUtil;

import java.sql.Connection;

public class IntegrationTest extends UnitTest {
    protected static final TestDatabase _database = new TestDatabase(new MysqlTestDatabase());

    protected final MainThreadPool _threadPool = new MainThreadPool(1, 1L);

    protected final MasterInflater _masterInflater;
    protected final FakeBlockStore _blockStore;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final FullNodeDatabaseManagerFactory _fullNodeDatabaseManagerFactory;
    protected final FullNodeDatabaseManagerFactory _readUncommittedDatabaseManagerFactory;
    protected final SpvDatabaseManagerFactory _spvDatabaseManagerFactory;
    protected final FakeSynchronizationStatus _synchronizationStatus;

    public IntegrationTest() {
        _masterInflater = new CoreInflater();
        _blockStore = new FakeBlockStore();
        _synchronizationStatus = new FakeSynchronizationStatus();

        _databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        _fullNodeDatabaseManagerFactory = new FullNodeDatabaseManagerFactory(_databaseConnectionFactory, _blockStore, _masterInflater);
        _spvDatabaseManagerFactory = new SpvDatabaseManagerFactory(_databaseConnectionFactory);

        final ReadUncommittedDatabaseConnectionFactory readUncommittedDatabaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactoryWrapper(_databaseConnectionFactory);
        _readUncommittedDatabaseManagerFactory = new FullNodeDatabaseManagerFactory(readUncommittedDatabaseConnectionFactory, _blockStore, _masterInflater);

        // Bypass the Hikari database connection pool...
        _database.setDatabaseConnectionPool(new DatabaseConnectionPool() {
            protected final MutableList<DatabaseConnection> _databaseConnections = new MutableList<DatabaseConnection>();

            @Override
            public DatabaseConnection newConnection() throws DatabaseException {
                final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
                _databaseConnections.add(databaseConnection);
                return databaseConnection;
            }

            @Override
            public void close() throws DatabaseException {
                try {
                    for (final DatabaseConnection databaseConnection : _databaseConnections) {
                        databaseConnection.close();
                    }
                }
                finally {
                    _databaseConnections.clear();
                }
            }
        });
    }

    static {
        IntegrationTest.resetDatabase();
    }

    public static void resetDatabase() {
        final DatabaseInitializer<Connection> databaseInitializer = new MysqlDatabaseInitializer("sql/full_node/init_mysql.sql", 2, BitcoinVerdeDatabase.DATABASE_UPGRADE_HANDLER);
        try {
            _database.reset();

            final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getMysqlDatabaseConnectionFactory();
            try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                databaseInitializer.initializeDatabase(databaseConnection);
            }
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void before() {
        IntegrationTest.resetDatabase();

        _synchronizationStatus.setState(State.ONLINE);
        _synchronizationStatus.setCurrentBlockHeight(Long.MAX_VALUE);
        _blockStore.clear();

        final Container<Long> uncommittedUtxoBlockHeight = ReflectionUtil.getStaticValue(UnspentTransactionOutputDatabaseManager.class, "UNCOMMITTED_UTXO_BLOCK_HEIGHT");
        uncommittedUtxoBlockHeight.value = 0L;
    }

    @Override
    public void after() { }
}

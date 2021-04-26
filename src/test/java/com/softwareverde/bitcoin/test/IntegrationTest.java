package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.ReadUncommittedDatabaseConnectionFactoryWrapper;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.main.BitcoinVerdeDatabase;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputJvmManager;
import com.softwareverde.bitcoin.test.fake.FakeSynchronizationStatus;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.MysqlDatabaseInitializer;
import com.softwareverde.database.mysql.connection.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.test.database.MysqlTestDatabase;
import com.softwareverde.test.database.TestDatabase;
import com.softwareverde.util.Container;
import com.softwareverde.util.ReflectionUtil;

import java.sql.Connection;
import java.util.List;

public class IntegrationTest extends UnitTest {
    protected static final TestDatabase _database = new TestDatabase(new MysqlTestDatabase());

    protected final CachedThreadPool _threadPool = new CachedThreadPool(1, 1L);

    protected final MasterInflater _masterInflater;
    protected final MockBlockStore _blockStore;
    protected final CheckpointConfiguration _checkpointConfiguration;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final FullNodeDatabaseManagerFactory _fullNodeDatabaseManagerFactory;
    protected final FullNodeDatabaseManagerFactory _readUncommittedDatabaseManagerFactory;
    protected final SpvDatabaseManagerFactory _spvDatabaseManagerFactory;
    protected final FakeSynchronizationStatus _synchronizationStatus;
    protected final FakeSynchronizationStatus _syncingSynchronizationStatus;
    protected final DifficultyCalculatorFactory _difficultyCalculatorFactory;
    protected final TransactionValidatorFactory _transactionValidatorFactory;

    protected Long _requiredCoinbaseMaturity = 0L;

    public IntegrationTest() {
        Logger.setLogLevel("com.zaxxer.hikari.pool", LogLevel.WARN);
        Logger.setLogLevel("com.zaxxer.hikari.pool", LogLevel.WARN);
        Logger.setLogLevel("ch.vorburger.exec", LogLevel.WARN);
        Logger.setLogLevel("ch.vorburger.mariadb4j", LogLevel.WARN);

        _masterInflater = new CoreInflater();
        _blockStore = new MockBlockStore();
        _synchronizationStatus = new FakeSynchronizationStatus();

        _syncingSynchronizationStatus = new FakeSynchronizationStatus();
        _syncingSynchronizationStatus.setState(State.SYNCHRONIZING);

        _checkpointConfiguration = new CheckpointConfiguration() {
            @Override
            public Boolean violatesCheckpoint(final Long blockHeight, final Sha256Hash blockHash) {
                return false;
            }
        };

        _databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        _fullNodeDatabaseManagerFactory = new FullNodeDatabaseManagerFactory(_databaseConnectionFactory, _database.getMaxQueryBatchSize(), _blockStore, _masterInflater, _checkpointConfiguration);
        _spvDatabaseManagerFactory = new SpvDatabaseManagerFactory(_databaseConnectionFactory, _database.getMaxQueryBatchSize(), _checkpointConfiguration);

        final ReadUncommittedDatabaseConnectionFactory readUncommittedDatabaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactoryWrapper(_databaseConnectionFactory);
        _readUncommittedDatabaseManagerFactory = new FullNodeDatabaseManagerFactory(readUncommittedDatabaseConnectionFactory, _database.getMaxQueryBatchSize(), _blockStore, _masterInflater, _checkpointConfiguration);

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

        _difficultyCalculatorFactory = new DifficultyCalculatorFactory() {
            @Override
            public DifficultyCalculator newDifficultyCalculator(final DifficultyCalculatorContext context) {
                return new DifficultyCalculator(context);
            }
        };

        _transactionValidatorFactory = new TransactionValidatorFactory() {
            @Override
            public TransactionValidator getTransactionValidator(final BlockOutputs blockOutputs, final TransactionValidator.Context transactionValidatorContext) {
                return new TransactionValidatorCore(blockOutputs, transactionValidatorContext) {
                    @Override
                    protected Long _getCoinbaseMaturity() {
                        return _requiredCoinbaseMaturity;
                    }
                };
            }
        };
    }

    static {
        IntegrationTest.resetDatabase();
    }

    public static void resetDatabase() {
        final DatabaseInitializer<Connection> databaseInitializer = new MysqlDatabaseInitializer("sql/node/mysql/init.sql", 2, BitcoinVerdeDatabase.DATABASE_UPGRADE_HANDLER);
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
    public void before() throws Exception {
        IntegrationTest.resetDatabase();

        _threadPool.start();
        _synchronizationStatus.setState(State.ONLINE);
        _synchronizationStatus.setCurrentBlockHeight(Long.MAX_VALUE);
        _blockStore.clear();

        // make sure UTXO set appears initialized
        final Container<Long> uncommittedUtxoBlockHeight = ReflectionUtil.getStaticValue(UnspentTransactionOutputJvmManager.class, "UNCOMMITTED_UTXO_BLOCK_HEIGHT");
        uncommittedUtxoBlockHeight.value = 0L;

        // Clear the static UTXO cache and the double buffer.
        new UnspentTransactionOutputJvmManager(null, 0.5F, null, null, null) {
            {
                final Thread doubleBufferThread = UnspentTransactionOutputJvmManager.DOUBLE_BUFFER_THREAD;
                if (doubleBufferThread != null) {
                    doubleBufferThread.join();
                }

                UnspentTransactionOutputJvmManager.UTXO_SET.clear();
                UnspentTransactionOutputJvmManager.DOUBLE_BUFFER.clear();
            }
        };
    }

    @Override
    public void after() throws Exception {
        Thread.sleep(500L); // Allow Integration DB to complete cleanup before next test.
        _threadPool.stop();
    }

    protected static final String INSERT_BLOCK_QUERY = "INSERT INTO blocks (hash, previous_block_id, block_height, blockchain_segment_id, merkle_root, version, timestamp, median_block_time, difficulty, nonce, chain_work) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    protected BlockId insertBlockHeader(final BlockchainSegmentId blockchainSegmentId, final BlockHeader blockHeader, final Long blockHeight, final Sha256Hash previousBlockHash, final ChainWork chainWork) throws DatabaseException {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockId previousBlockId;
            {
                final List<Row> rows = databaseConnection.query(
                    new Query("SELECT id FROM blocks WHERE hash = ?")
                        .setParameter(previousBlockHash)
                );
                if (! rows.isEmpty()) {
                    final Row row = rows.get(0);
                    previousBlockId = BlockId.wrap(row.getLong("id"));
                }
                else {
                    final BlockHeaderInflater blockHeaderInflater = _masterInflater.getBlockHeaderInflater();
                    final BlockHeader dummyBlockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.GENESIS_BLOCK));
                    final ChainWork dummyChainWork = ChainWork.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");

                    final BlockId dummyPreviousBlockId;
                    {
                        final List<Row> dummyPreviousBlockRows = databaseConnection.query(
                            new Query("SELECT id FROM blocks WHERE blockchain_segment_id = ? ORDER BY block_height DESC LIMIT 1")
                                .setParameter(blockchainSegmentId)
                        );
                        if (! dummyPreviousBlockRows.isEmpty()) {
                            final Row row = dummyPreviousBlockRows.get(0);
                            dummyPreviousBlockId = BlockId.wrap(row.getLong("id"));
                        }
                        else {
                            dummyPreviousBlockId = null;
                        }
                    }

                    previousBlockId = BlockId.wrap(databaseConnection.executeSql(
                        new Query(INSERT_BLOCK_QUERY)
                            .setParameter(previousBlockHash)
                            .setParameter(dummyPreviousBlockId)
                            .setParameter(blockHeight - 1L)
                            .setParameter(blockchainSegmentId)
                            .setParameter(dummyBlockHeader.getMerkleRoot())
                            .setParameter(dummyBlockHeader.getVersion())
                            .setParameter(dummyBlockHeader.getTimestamp())
                            .setParameter(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP)
                            .setParameter(dummyBlockHeader.getDifficulty())
                            .setParameter(dummyBlockHeader.getNonce())
                            .setParameter(dummyChainWork)
                    ));
                }
            }

            final Difficulty difficulty = blockHeader.getDifficulty();

            return BlockId.wrap(databaseConnection.executeSql(
                new Query(INSERT_BLOCK_QUERY)
                    .setParameter(blockHeader.getHash())
                    .setParameter(previousBlockId)
                    .setParameter(blockHeight)
                    .setParameter(blockchainSegmentId)
                    .setParameter(blockHeader.getMerkleRoot())
                    .setParameter(blockHeader.getVersion())
                    .setParameter(blockHeader.getTimestamp())
                    .setParameter(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP)
                    .setParameter(difficulty)
                    .setParameter(blockHeader.getNonce())
                    .setParameter(chainWork)
            ));
        }
    }
}

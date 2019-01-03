package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.server.database.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.output.LockingScriptId;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.timer.MilliTimer;

public class AddressProcessor extends SleepyService {
    public static final Integer BATCH_SIZE = 4096;

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    public AddressProcessor(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final MilliTimer processTimer = new MilliTimer();

            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection, _databaseManagerCache);
            final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(databaseConnection, _databaseManagerCache);
            final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

            final Integer lockingScriptCount;
            processTimer.start();
            TransactionUtil.startTransaction(databaseConnection);
            {
                final List<LockingScriptId> lockingScriptIds = transactionOutputDatabaseManager.getLockingScriptsWithUnprocessedTypes(BATCH_SIZE);
                if (lockingScriptIds.isEmpty()) { return false; }

                lockingScriptCount = lockingScriptIds.getSize();

                final List<LockingScript> lockingScripts = transactionOutputDatabaseManager.getLockingScripts(lockingScriptIds);

                final List<ScriptType> scriptTypes;
                {
                    final ImmutableListBuilder<ScriptType> scriptTypesBuilder = new ImmutableListBuilder<ScriptType>(lockingScriptCount);
                    for (final LockingScript lockingScript : lockingScripts) {
                        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                        scriptTypesBuilder.add(scriptType);
                    }
                    scriptTypes = scriptTypesBuilder.build();
                }

                final List<AddressId> addressIds = addressDatabaseManager.storeScriptAddresses(lockingScripts);

                transactionOutputDatabaseManager.setLockingScriptTypes(lockingScriptIds, scriptTypes, addressIds);
            }
            TransactionUtil.commitTransaction(databaseConnection);
            processTimer.stop();

            Logger.log("Processed " + lockingScriptCount + " LockingScript Addresses in " + processTimer.getMillisecondsElapsed() + "ms.");
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }

        return true;
    }

    @Override
    protected void _onSleep() { }
}

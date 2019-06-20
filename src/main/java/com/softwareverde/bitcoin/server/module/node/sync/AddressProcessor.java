package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.address.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.output.LockingScriptId;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.timer.MilliTimer;

public class AddressProcessor extends SleepyService {
    public static final Integer BATCH_SIZE = 4096;

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public AddressProcessor(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final MilliTimer processTimer = new MilliTimer();

            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = databaseManager.getTransactionOutputDatabaseManager();
            final AddressDatabaseManager addressDatabaseManager = databaseManager.getAddressDatabaseManager();
            final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

            final int lockingScriptCount;
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

package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.address.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.LockingScriptId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;

public class AddressProcessor extends SleepyService {
    public static final Integer BATCH_SIZE = 4096;

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected Runnable _onSleepCallback;

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
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = databaseManager.getTransactionOutputDatabaseManager();
            final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();
            final AddressDatabaseManager addressDatabaseManager = databaseManager.getAddressDatabaseManager();
            final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
            final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

            final int lockingScriptCount;
            processTimer.start();
            TransactionUtil.startTransaction(databaseConnection);
            {
                final List<LockingScriptId> lockingScriptIds = transactionOutputDatabaseManager.getLockingScriptsWithUnprocessedTypes(BATCH_SIZE);
                if (lockingScriptIds.isEmpty()) { return false; }

                lockingScriptCount = lockingScriptIds.getSize();

                final List<LockingScript> lockingScripts = transactionOutputDatabaseManager.getLockingScripts(lockingScriptIds);

                final HashMap<TransactionId, MutableList<TransactionOutputId>> slpTransactionOutputs = new HashMap<TransactionId, MutableList<TransactionOutputId>>();
                final List<TransactionId> slpTokenTransactionIds;
                final List<ScriptType> scriptTypes;
                {
                    final ImmutableListBuilder<TransactionId> slpTokenTransactionIdsBuilder = new ImmutableListBuilder<TransactionId>();
                    final ImmutableListBuilder<ScriptType> scriptTypesBuilder = new ImmutableListBuilder<ScriptType>(lockingScriptCount);

                    for (int i = 0; i < lockingScriptCount; ++i) {
                        final LockingScriptId lockingScriptId = lockingScriptIds.get(i);
                        final LockingScript lockingScript = lockingScripts.get(i);

                        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                        scriptTypesBuilder.add(scriptType);

                        if (ScriptType.isSlpScriptType(scriptType)) {
                            final TransactionId slpTokenTransactionId = slpTransactionDatabaseManager.calculateSlpTokenGenesisTransactionId(lockingScriptId, lockingScript);

                            boolean slpTransactionIsValid;
                            { // Validate SLP Transaction...
                                // NOTE: Inflating the whole transaction is mildly costly, but typically this only happens once per SLP transaction, which is required anyway.
                                final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.getTransactionOutputId(lockingScriptId);
                                final TransactionId transactionId = transactionOutputDatabaseManager.getTransactionId(transactionOutputId);
                                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                                if (transaction == null) {
                                    slpTransactionIsValid = false;
                                }
                                else {
                                    final SlpScript slpScript = slpScriptInflater.fromLockingScript(lockingScript);

                                    final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                                    final int transactionOutputCount = transactionOutputs.getSize();

                                    slpTransactionIsValid = ( (slpScript != null) && (transactionOutputCount >= slpScript.getMinimumTransactionOutputCount()) );

                                    if (slpTransactionIsValid) {
                                        switch (slpScript.getType()) {
                                            case GENESIS: {
                                                final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) slpScript;
                                                final Integer generatorOutputIndex = slpGenesisScript.getGeneratorOutputIndex();

                                                final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
                                                if ( (generatorOutputIndex != null) && (generatorOutputIndex >= transactionOutputIds.getSize())) {
                                                    slpTransactionIsValid = false;
                                                }
                                                else {
                                                    { // Mark the Receiving Output as an SLP Output...
                                                        final TransactionOutputId siblingTransactionOutputId = transactionOutputIds.get(SlpGenesisScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                                                        ConstUtil.addToListMap(slpTokenTransactionId, siblingTransactionOutputId, slpTransactionOutputs);
                                                    }

                                                    if (generatorOutputIndex != null) {
                                                        // Mark the Mint Baton Output as an SLP Output...
                                                        final TransactionOutputId siblingTransactionOutputId = transactionOutputIds.get(generatorOutputIndex);
                                                        ConstUtil.addToListMap(slpTokenTransactionId, siblingTransactionOutputId, slpTransactionOutputs);
                                                    }
                                                }
                                            } break;

                                            case MINT: {
                                                final SlpMintScript slpMintScript = (SlpMintScript) slpScript;
                                                final Integer generatorOutputIndex = slpMintScript.getGeneratorOutputIndex();

                                                final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
                                                if ( (generatorOutputIndex != null) && (generatorOutputIndex >= transactionOutputIds.getSize())) {
                                                    slpTransactionIsValid = false;
                                                }
                                                else {
                                                    { // Mark the Receiving Output as an SLP Output...
                                                        final TransactionOutputId siblingTransactionOutputId = transactionOutputIds.get(SlpMintScript.RECEIVER_TRANSACTION_OUTPUT_INDEX);
                                                        ConstUtil.addToListMap(slpTokenTransactionId, siblingTransactionOutputId, slpTransactionOutputs);
                                                    }

                                                    if (generatorOutputIndex != null) {
                                                        // Mark the Mint Baton Output as an SLP Output...
                                                        final TransactionOutputId siblingTransactionOutputId = transactionOutputIds.get(generatorOutputIndex);
                                                        ConstUtil.addToListMap(slpTokenTransactionId, siblingTransactionOutputId, slpTransactionOutputs);
                                                    }
                                                }
                                            } break;

                                            case SEND: {
                                                final SlpSendScript slpSendScript = (SlpSendScript) slpScript;

                                                final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
                                                int slpTransactionOutputIndex = 0;
                                                for (final TransactionOutputId siblingTransactionOutputId : transactionOutputIds) {
                                                    final Long slpAmount = Util.coalesce(slpSendScript.getAmount(slpTransactionOutputIndex));

                                                    if (slpAmount > 0L) {
                                                        ConstUtil.addToListMap(slpTokenTransactionId, siblingTransactionOutputId, slpTransactionOutputs);
                                                    }

                                                    slpTransactionOutputIndex += 1;
                                                }
                                            } break;

                                            case COMMIT: { } break;
                                        }
                                    }
                                }
                            }

                            slpTokenTransactionIdsBuilder.add(slpTransactionIsValid ? slpTokenTransactionId : null);
                        }
                        else {
                            slpTokenTransactionIdsBuilder.add(null);
                        }
                    }
                    scriptTypes = scriptTypesBuilder.build();
                    slpTokenTransactionIds = slpTokenTransactionIdsBuilder.build();
                }

                final List<AddressId> addressIds = addressDatabaseManager.storeScriptAddresses(lockingScripts);

                transactionOutputDatabaseManager.setLockingScriptTypes(lockingScriptIds, scriptTypes, addressIds, slpTokenTransactionIds);

                for (final TransactionId slpGenesisTransactionId : slpTransactionOutputs.keySet()) {
                    final List<TransactionOutputId> transactionOutputIds = slpTransactionOutputs.get(slpGenesisTransactionId);
                    slpTransactionDatabaseManager.setSlpTransactionIds(slpGenesisTransactionId, transactionOutputIds);
                }
            }
            TransactionUtil.commitTransaction(databaseConnection);
            processTimer.stop();

            Logger.info("Processed " + lockingScriptCount + " LockingScript Addresses in " + processTimer.getMillisecondsElapsed() + "ms.");
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return false;
        }

        return true;
    }

    @Override
    protected void _onSleep() {
        final Runnable onSleepCallback = _onSleepCallback;
        if (onSleepCallback != null) {
            onSleepCallback.run();
        }
    }

    public void setOnSleepCallback(final Runnable onSleepCallback) {
        _onSleepCallback = onSleepCallback;
    }
}

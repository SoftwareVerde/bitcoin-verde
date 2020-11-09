package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.slp.QuerySlpStatusMessage;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SlpValidity;
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SpvTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.manager.NodeManager;
import com.softwareverde.util.Util;

import java.util.concurrent.TimeUnit;

public class SpvSlpTransactionValidator extends SleepyService {
    protected final SpvDatabaseManagerFactory _databaseManagerFactory;
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final Object _sleepPin = new Object();

    public SpvSlpTransactionValidator(final SpvDatabaseManagerFactory spvDatabaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager) {
        _databaseManagerFactory = spvDatabaseManagerFactory;
        _bitcoinNodeManager = bitcoinNodeManager;
    }

    @Override
    public synchronized void wakeUp() {
        synchronized (_sleepPin) {
            _sleepPin.notifyAll();
        }

        super.wakeUp();
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        try (final SpvDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final List<Sha256Hash> unknownValidityTransactionHashes = transactionDatabaseManager.getSlpTransactionsWithSlpStatus(SlpValidity.UNKNOWN);

            if (unknownValidityTransactionHashes.isEmpty()) {
                return false;
            }

            final BitcoinNode bitcoinNode = _bitcoinNodeManager.getNode(new NodeManager.NodeFilter<BitcoinNode>() {
                @Override
                public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                    final Boolean isSlpIndexer = bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.SLP_INDEX_ENABLED);
                    return Util.coalesce(isSlpIndexer, false);
                }
            });
            if (bitcoinNode == null) {
                // unable to find an appropriate node
                Logger.debug("Unable to request SLP validity of " + unknownValidityTransactionHashes.getCount() + " transactions: no SLP indexing nodes available.");
                Thread.sleep(TimeUnit.SECONDS.toMillis(20));
                return true;
            }

            // request in batches of QuerySlpStatusMessage.MAX_HASH_COUNT
            Logger.info("Requesting SLP status of " + unknownValidityTransactionHashes.getCount() + " transactions from: " + bitcoinNode.getConnectionString());
            int startIndex = 0;
            while (startIndex < unknownValidityTransactionHashes.getCount()) {
                final MutableList<Sha256Hash> batchOfHashes = new MutableList<Sha256Hash>();
                for (int i = 0; (i < QuerySlpStatusMessage.MAX_HASH_COUNT) && ((startIndex + i) < unknownValidityTransactionHashes.getCount()); i++) {
                    batchOfHashes.add(unknownValidityTransactionHashes.get(startIndex + i));
                }
                bitcoinNode.getSlpStatus(batchOfHashes);

                startIndex += batchOfHashes.getCount();
            }

            final long oneMinuteInMilliseconds = TimeUnit.MINUTES.toMillis(1);
            synchronized (_sleepPin) {
                _sleepPin.wait(oneMinuteInMilliseconds);
            }

            return true;
        }
        catch (final Exception exception) {
            Logger.debug("Problem synchronizing SLP Validity.", exception);
            return false;
        }
    }

    @Override
    protected void _onSleep() { }
}

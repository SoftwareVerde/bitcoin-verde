package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentManager;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.message.type.query.utxo.NodeSpecificUtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentsMessage;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class QueryUtxoCommitmentsHandler implements BitcoinNode.QueryUtxoCommitmentsHandler {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public QueryUtxoCommitmentsHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public void run(final BitcoinNode bitcoinNode) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UtxoCommitmentManager utxoCommitmentManager = databaseManager.getUtxoCommitmentManager();

            final List<NodeSpecificUtxoCommitmentBreakdown> utxoCommitments = utxoCommitmentManager.getAvailableUtxoCommitments();
            if (utxoCommitments.isEmpty()) { return; }

            final UtxoCommitmentsMessage utxoCommitmentsMessage = new UtxoCommitmentsMessage();
            for (final NodeSpecificUtxoCommitmentBreakdown utxoCommitmentBreakdown : utxoCommitments) {
                final UtxoCommitmentMetadata utxoCommitmentMetadata = utxoCommitmentBreakdown.getMetadata();
                final List<UtxoCommitmentBucket> utxoCommitmentBuckets = utxoCommitmentBreakdown.getBuckets();
                utxoCommitmentsMessage.addUtxoCommitment(utxoCommitmentMetadata, utxoCommitmentBuckets);
            }

            bitcoinNode.queueMessage(utxoCommitmentsMessage);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }
}

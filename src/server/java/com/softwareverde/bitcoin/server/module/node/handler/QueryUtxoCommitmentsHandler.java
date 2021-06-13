package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentManager;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentBreakdown;
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

            final List<UtxoCommitmentBreakdown> utxoCommitments = utxoCommitmentManager.getAvailableUtxoCommitments();
            if (utxoCommitments.isEmpty()) { return; }

            final UtxoCommitmentsMessage utxoCommitmentsMessage = new UtxoCommitmentsMessage();
            for (final UtxoCommitmentBreakdown utxoCommitmentBreakdown : utxoCommitments) {
                utxoCommitmentsMessage.addUtxoCommitment(utxoCommitmentBreakdown.commitment, utxoCommitmentBreakdown.buckets);
            }

            bitcoinNode.queueMessage(utxoCommitmentsMessage);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }
}

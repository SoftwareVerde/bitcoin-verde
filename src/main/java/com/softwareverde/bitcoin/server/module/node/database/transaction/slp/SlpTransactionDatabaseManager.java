package com.softwareverde.bitcoin.server.module.node.database.transaction.slp;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.LockingScriptId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.commit.SlpCommitScript;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.bitcoin.transaction.script.slp.mint.SlpMintScript;
import com.softwareverde.bitcoin.transaction.script.slp.send.SlpSendScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.database.util.DatabaseUtil;
import com.softwareverde.util.Util;

import java.util.LinkedHashMap;

public class SlpTransactionDatabaseManager {
    protected final FullNodeDatabaseManager _databaseManager;

    /**
     * Returns the TransactionId of the SLP Token's Genesis Transaction.
     *  If the TransactionId cannot be found then null is returned.
     *  At least `nullableTransactionId` or `nullableLockingScriptId` must be provided.
     *  For better performance, provide `nullableLockingScript` when available.
     */
    protected TransactionId _calculateSlpTokenGenesisTransactionId(final TransactionId nullableTransactionId, final LockingScriptId nullableLockingScriptId, final LockingScript nullableLockingScript) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

        final LockingScript lockingScript = ( (nullableLockingScript != null) ? nullableLockingScript : transactionOutputDatabaseManager.getLockingScript(nullableLockingScriptId));
        if (lockingScript == null) { return null; }

        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

        final TransactionId slpTokenTransactionId;
        switch (scriptType) {
            case SLP_GENESIS_SCRIPT: {
                final SlpGenesisScript slpGenesisScript = slpScriptInflater.genesisScriptFromScript(lockingScript);
                if (slpGenesisScript != null) {
                    slpTokenTransactionId = (nullableTransactionId != null ? nullableTransactionId : transactionOutputDatabaseManager.getTransactionId(nullableLockingScriptId));
                }
                else {
                    slpTokenTransactionId = null;
                }
            } break;

            case SLP_MINT_SCRIPT: {
                final SlpMintScript slpMintScript = slpScriptInflater.mintScriptFromScript(lockingScript);
                final SlpTokenId slpTokenId = (slpMintScript != null ? slpMintScript.getTokenId() : null);
                slpTokenTransactionId = (slpTokenId != null ? transactionDatabaseManager.getTransactionId(slpTokenId) : null);
            } break;

            case SLP_COMMIT_SCRIPT: {
                final SlpCommitScript slpCommitScript = slpScriptInflater.commitScriptFromScript(lockingScript);
                final SlpTokenId slpTokenId = (slpCommitScript != null ? slpCommitScript.getTokenId() : null);
                slpTokenTransactionId = (slpTokenId != null ? transactionDatabaseManager.getTransactionId(slpTokenId) : null);
            } break;

            case SLP_SEND_SCRIPT: {
                final SlpSendScript slpSendScript = slpScriptInflater.sendScriptFromScript(lockingScript);
                final SlpTokenId slpTokenId = (slpSendScript != null ? slpSendScript.getTokenId() : null);
                slpTokenTransactionId = (slpTokenId != null ? transactionDatabaseManager.getTransactionId(slpTokenId) : null);
            } break;

            default: { slpTokenTransactionId = null; }
        }

        return slpTokenTransactionId;
    }

    public SlpTransactionDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public Boolean getSlpTransactionValidationResult(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, blockchain_segment_id, is_valid FROM validated_slp_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        for (final Row row : rows) {
            final BlockchainSegmentId slpBlockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));

            final Boolean isConnectedToChain = blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId, slpBlockchainSegmentId, BlockRelationship.ANY);
            if (isConnectedToChain) {
                return row.getBoolean("is_valid");
            }
        }

        return null;
    }

    public void setSlpTransactionValidationResult(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId, final Boolean isValid) throws DatabaseException {
        if (blockchainSegmentId == null) { return; }
        if (transactionId == null) { return; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Integer isValidIntegerValue = ( (isValid != null) ? (isValid ? 1 : 0) : null );

        databaseConnection.executeSql(
            new Query("INSERT INTO validated_slp_transactions (transaction_id, blockchain_segment_id, is_valid) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE is_valid = ?")
                .setParameter(transactionId)
                .setParameter(blockchainSegmentId)
                .setParameter(isValidIntegerValue)
                .setParameter(isValidIntegerValue)
        );
    }

    public TransactionId calculateSlpTokenGenesisTransactionId(final LockingScriptId lockingScriptId, final LockingScript nullableLockingScript) throws DatabaseException {
        return _calculateSlpTokenGenesisTransactionId(null, lockingScriptId, nullableLockingScript);
    }

    public TransactionId calculateSlpTokenGenesisTransactionId(final TransactionId transactionId, final LockingScript nullableLockingScript) throws DatabaseException {
        return _calculateSlpTokenGenesisTransactionId(transactionId, null, nullableLockingScript);
    }

    public SlpTokenId getSlpTokenId(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, slp_transaction_id FROM locking_scripts WHERE transaction_output_id = ?")
                .setParameter(transactionOutputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final TransactionId slpTransactionId = TransactionId.wrap(row.getLong("slp_transaction_id"));

        final Sha256Hash slpTokenId = transactionDatabaseManager.getTransactionHash(slpTransactionId);
        return SlpTokenId.wrap(slpTokenId);
    }

    public SlpGenesisScript getSlpGenesisScript(final SlpTokenId slpTokenId) throws DatabaseException {
        if (slpTokenId == null) { return null; }

        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(slpTokenId);
        if (transactionId == null) { return null; }

        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
        final TransactionOutput transactionOutput = transactionOutputDatabaseManager.getTransactionOutput(transactionId, 0);
        if (transactionOutput == null) { return null; }

        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();
        final LockingScript lockingScript = transactionOutput.getLockingScript();
        return slpScriptInflater.genesisScriptFromScript(lockingScript);
    }

    public SlpTokenId getSlpTokenId(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT locking_scripts.slp_transaction_id FROM locking_scripts INNER JOIN transaction_outputs ON locking_scripts.transaction_output_id = transaction_outputs.id WHERE transaction_outputs.transaction_id = ? AND transaction_outputs.`index` = 0")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final TransactionId slpTransactionId = TransactionId.wrap(row.getLong("slp_transaction_id"));

        final Sha256Hash slpTokenId = transactionDatabaseManager.getTransactionHash(slpTransactionId);
        return SlpTokenId.wrap(slpTokenId);
    }

    public void setSlpTransactionId(final TransactionId slpTokenTransactionId, final TransactionOutputId transactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE locking_scripts SET slp_transaction_id = ? WHERE transaction_output_id = ?")
                .setParameter(slpTokenTransactionId)
                .setParameter(transactionOutputId)
        );
    }

    public void setSlpTransactionIds(final TransactionId slpTokenTransactionId, final List<TransactionOutputId> transactionOutputIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE locking_scripts SET slp_transaction_id = ? WHERE transaction_output_id IN (" + DatabaseUtil.createInClause(transactionOutputIds) + ")")
                .setParameter(slpTokenTransactionId)
        );
    }

    /**
     * Returns a mapping of (SLP) TransactionIds that have not been validated yet, ordered by their respective block's height.
     *  Unconfirmed transactions are not returned by this function.
     */
    public LinkedHashMap<BlockId, List<TransactionId>> getPendingValidationSlpTransactions(final Integer maxCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id AS block_id, transaction_outputs.transaction_id FROM locking_scripts INNER JOIN transaction_outputs ON (transaction_outputs.id = locking_scripts.transaction_output_id) LEFT OUTER JOIN validated_slp_transactions ON (validated_slp_transactions.transaction_id = transaction_outputs.transaction_id) INNER JOIN block_transactions ON (block_transactions.transaction_id = transaction_outputs.transaction_id) INNER JOIN blocks ON (blocks.id = block_transactions.block_id) WHERE validated_slp_transactions.id IS NULL AND locking_scripts.slp_transaction_id IS NOT NULL GROUP BY transaction_outputs.transaction_id ORDER BY blocks.block_height ASC LIMIT " + maxCount)
        );

        final LinkedHashMap<BlockId, List<TransactionId>> result = new LinkedHashMap<BlockId, List<TransactionId>>();

        BlockId previousBlockId = null;
        ImmutableListBuilder<TransactionId> transactionIds = null;

        for (final Row row : rows) {
            final BlockId blockId = BlockId.wrap(row.getLong("block_id"));
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            if ( (blockId == null) || (transactionId == null) ) { continue; }

            if ( (previousBlockId == null) || (! Util.areEqual(previousBlockId, blockId)) ) {
                if (transactionIds != null) {
                    result.put(previousBlockId, transactionIds.build());
                }
                previousBlockId = blockId;
                transactionIds = new ImmutableListBuilder<TransactionId>();
            }

            transactionIds.add(transactionId);
        }

        if ( (previousBlockId != null) && (transactionIds != null) ) {
            result.put(previousBlockId, transactionIds.build());
        }

        return result;
    }
}

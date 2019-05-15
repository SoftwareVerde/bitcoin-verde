package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.io.Logger;
import com.softwareverde.util.timer.NanoTimer;

public class CacheWarmer {
    public void warmUpCache(final MasterDatabaseManagerCache masterDatabaseManagerCache, final DatabaseConnectionFactory databaseConnectionFactory) {
        final Runtime runtime = Runtime.getRuntime();

        try (final LocalDatabaseManagerCache localDatabaseManagerCache = new LocalDatabaseManagerCache(masterDatabaseManagerCache);
                final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {

            { // Warm Up UTXO Cache...
                final UnspentTransactionOutputCache unspentTransactionOutputCache = localDatabaseManagerCache.getUnspentTransactionOutputCache();

                final Long newestUnspentTransactionOutputId;
                {
                    final java.util.List<Row> rows = databaseConnection.query(
                        new Query("SELECT id FROM unspent_transaction_outputs ORDER BY id DESC LIMIT 1")
                    );
                    if (rows.isEmpty()) {
                        newestUnspentTransactionOutputId = 0L;
                    }
                    else {
                        final Row row = rows.get(0);
                        newestUnspentTransactionOutputId = row.getLong("id");
                    }
                }

                final Long maxUtxoCount = masterDatabaseManagerCache.getMaxCachedUtxoCount().unwrap();

                final Integer batchSize = 4096; // 512; // NOTE: Reducing the batch size greatly decreases the amount of memory-bloat during startup.
                Long lastRowId = (newestUnspentTransactionOutputId + 1L);

                long cachedCount = 0L;
                while (cachedCount < maxUtxoCount) {
                    final NanoTimer nanoTimer = new NanoTimer();
                    nanoTimer.start();

                    Long batchFirstRowId = null;
                    final java.util.List<Row> rows = databaseConnection.query(
                        new Query("SELECT id, transaction_output_id, transaction_hash, `index` FROM unspent_transaction_outputs WHERE id < ? ORDER BY id DESC LIMIT " + batchSize)
                            .setParameter(lastRowId)
                    );
                    if (rows.isEmpty()) { break; }

                    for (final Row row : rows) {
                        final Long rowId = row.getLong("id");
                        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("transaction_output_id"));
                        final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("transaction_hash"));
                        final Integer transactionOutputIndex = row.getInteger("index");

                        final Long sortOrder = (maxUtxoCount - cachedCount);
                        unspentTransactionOutputCache.cacheUnspentTransactionOutputId(sortOrder, transactionHash, transactionOutputIndex, transactionOutputId);
                        lastRowId = rowId;

                        if (batchFirstRowId == null) {
                            batchFirstRowId = rowId;
                        }

                        cachedCount += 1;
                        if (cachedCount >= maxUtxoCount) { break; }
                    }

                    nanoTimer.stop();
                    Logger.log("Cached: " + batchFirstRowId + " - " + lastRowId + " (" + cachedCount + " of " + maxUtxoCount + ") (" + (cachedCount / maxUtxoCount.floatValue() * 100.0F) + "%) (" + nanoTimer.getMillisecondsElapsed() + "ms)");
                }
            }

            masterDatabaseManagerCache.commitLocalDatabaseManagerCache(localDatabaseManagerCache);
            masterDatabaseManagerCache.commit();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            BitcoinUtil.exitFailure();
        }
    }
}
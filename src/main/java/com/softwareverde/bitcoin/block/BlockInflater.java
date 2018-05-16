package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public class BlockInflater {
    protected MutableBlock _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
        if (blockHeader == null) { return null; }

        final Long transactionCount = byteArrayReader.readVariableSizedInteger();
        final MutableList<Transaction> transactions = new MutableList<Transaction>(transactionCount.intValue());

        for (long i=0; i<transactionCount; ++i) {
            final Transaction transaction = transactionInflater.fromBytes(byteArrayReader);
            if (transaction == null) { return null; }

            transactions.add(transaction);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return new MutableBlock(blockHeader, transactions);
    }

    protected List<Transaction> _getBlockTransactions(final BlockId blockId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final TransactionInflater transactionInflater = new TransactionInflater();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE block_id = ?")
                .setParameter(blockId)
        );

        final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Transaction transaction = transactionInflater.fromDatabaseConnection(transactionId, databaseConnection);
            listBuilder.add(transaction);
        }
        return listBuilder.build();
    }

    public MutableBlock fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public MutableBlock fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }

    public MutableBlock fromDatabaseConnection(final BlockId blockId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final BlockHeader blockHeader = blockHeaderInflater.fromDatabaseConnection(blockId, databaseConnection);
        if (blockHeader == null) { return null; }

        final List<Transaction> transactions = _getBlockTransactions(blockId, databaseConnection);

        return new MutableBlock(blockHeader, transactions);
    }
}

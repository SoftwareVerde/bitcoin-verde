package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.ScriptInflater;
import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.bytearray.Endian;

import java.util.List;

public class TransactionOutputInflater {
    protected TransactionOutput _fromByteArrayReader(final Integer index, final ByteArrayReader byteArrayReader) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();

        transactionOutput._amount = byteArrayReader.readLong(8, Endian.LITTLE);
        transactionOutput._index = index;

        final Integer scriptByteCount = byteArrayReader.readVariableSizedInteger().intValue();
        transactionOutput._lockingScript = new ImmutableLockingScript(byteArrayReader.readBytes(scriptByteCount, Endian.BIG));

        if (byteArrayReader.didOverflow()) { return null; }

        return transactionOutput;
    }

    public TransactionOutput fromBytes(final Integer index, final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(index, byteArrayReader);
    }

    public TransactionOutput fromBytes(final Integer index, final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(index, byteArrayReader);
    }

    public TransactionOutput fromDatabaseConnection(final TransactionOutputId transactionOutputId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final ScriptInflater scriptInflater = new ScriptInflater();

        final List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM transaction_outputs WHERE id = ?")
                .setParameter(transactionOutputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);

        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();

        final Long amount = row.getLong("amount");
        final Integer index = row.getInteger("index");

        final LockingScript lockingScript = LockingScript.castFrom(scriptInflater.fromBytes(row.getBytes("locking_script")));

        transactionOutput._amount = amount;
        transactionOutput._index = index;

        transactionOutput._lockingScript = lockingScript;

        return transactionOutput;
    }
}

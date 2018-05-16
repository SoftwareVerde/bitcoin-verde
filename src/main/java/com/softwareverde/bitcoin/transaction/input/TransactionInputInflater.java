package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.ScriptInflater;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.bytearray.Endian;

import java.util.List;

public class TransactionInputInflater {
    protected MutableTransactionInput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableTransactionInput transactionInput = new MutableTransactionInput();

        transactionInput._previousOutputTransactionHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
        transactionInput._previousOutputIndex = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer scriptByteCount = byteArrayReader.readVariableSizedInteger().intValue();
        transactionInput._unlockingScript = new ImmutableUnlockingScript(byteArrayReader.readBytes(scriptByteCount, Endian.BIG));
        transactionInput._sequenceNumber = byteArrayReader.readLong(4, Endian.LITTLE);

        if (byteArrayReader.didOverflow()) { return null; }

        return transactionInput;
    }

    public TransactionInput fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public TransactionInput fromBytes(final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }

    public TransactionInput fromDatabaseConnection(final TransactionInputId transactionInputId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final ScriptInflater scriptInflater = new ScriptInflater();

        final List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);

        final MutableTransactionInput transactionInput = new MutableTransactionInput();

        final Sha256Hash previousOutputTransactionHash;
        final Integer previousOutputIndex;
        {
            final List<Row> previousOutputTransactionRows = databaseConnection.query(
                new Query("SELECT transaction_outputs.id, transactions.hash, transaction_outputs.`index` FROM transaction_outputs INNER JOIN transactions ON (transaction_outputs.transaction_id = transactions.id) WHERE transaction_outputs.id = ?")
                    .setParameter(row.getLong("previous_transaction_output_id"))
            );
            if (previousOutputTransactionRows.isEmpty()) {
                previousOutputTransactionHash = new ImmutableSha256Hash();
                previousOutputIndex = -1;
            }
            else {
                final Row previousOutputTransactionRow = previousOutputTransactionRows.get(0);
                previousOutputTransactionHash = MutableSha256Hash.fromHexString(previousOutputTransactionRow.getString("hash"));
                previousOutputIndex = previousOutputTransactionRow.getInteger("index");
            }
        }

        final UnlockingScript unlockingScript = UnlockingScript.castFrom(scriptInflater.fromBytes(row.getBytes("unlocking_script")));
        final Long sequenceNumber = row.getLong("sequence_number");

        transactionInput._previousOutputTransactionHash = previousOutputTransactionHash;
        transactionInput._previousOutputIndex = previousOutputIndex;

        transactionInput._unlockingScript = unlockingScript;
        transactionInput._sequenceNumber = sequenceNumber;

        return transactionInput;
    }
}

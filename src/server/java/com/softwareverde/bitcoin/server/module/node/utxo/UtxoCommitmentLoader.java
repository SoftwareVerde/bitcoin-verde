package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.bytearray.ByteArrayStream;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class UtxoCommitmentLoader {
    public void createLoadFile(final List<File> utxoCommitmentFiles, final File outputLoadFile) throws IOException {
        final CommittedUnspentTransactionOutputInflater utxoInflater = new CommittedUnspentTransactionOutputInflater();

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        long bytesWrittenCount = 0L;
        try (
            final FileOutputStream fileOutputStream = new FileOutputStream(outputLoadFile);
            final ByteArrayStream byteArrayStream = new ByteArrayStream()
        ) {
            for (final File inputFile : utxoCommitmentFiles) {
                final FileInputStream fileInputStream = new FileInputStream(inputFile);
                byteArrayStream.appendInputStream(fileInputStream);
            }

            while (true) {
                final CommittedUnspentTransactionOutput utxo = utxoInflater.fromByteArrayReader(byteArrayStream);
                if (utxo == null) { break; }

                // transaction_hash BINARY(32) NOT NULL
                // `index` INT UNSIGNED NOT NULL
                // block_height INT UNSIGNED NOT NULL
                // is_coinbase TINYINT(1) NOT NULL DEFAULT 0
                // amount BIGINT NOT NULL
                // locking_script BLOB NOT NULL

                final String separator = "\t";

                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(utxo.getTransactionHash());
                stringBuilder.append(separator);
                stringBuilder.append(utxo.getIndex());
                stringBuilder.append(separator);
                stringBuilder.append(utxo.getBlockHeight());
                stringBuilder.append(separator);
                stringBuilder.append(utxo.isCoinbaseTransaction() ? "1" : "0");
                stringBuilder.append(separator);
                stringBuilder.append(utxo.getAmount());
                stringBuilder.append(separator);
                stringBuilder.append(utxo.getLockingScript());
                stringBuilder.append(System.lineSeparator());

                final byte[] bytes = StringUtil.stringToBytes(stringBuilder.toString());
                fileOutputStream.write(bytes);
                bytesWrittenCount += bytes.length;
            }

            fileOutputStream.flush();
        }

        nanoTimer.stop();
        Logger.trace("Wrote " + bytesWrittenCount + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
    }

    public void loadFile(final File loadFile, final DatabaseConnection databaseConnection) throws DatabaseException {
        final String loadFilePath = loadFile.getAbsolutePath();
        if ( (! loadFile.exists()) || (! loadFile.canRead()) ) {
            throw new DatabaseException("Unable to access loadFile: " + loadFilePath);
        }

        databaseConnection.executeSql(
            new Query("LOAD DATA INFILE ? INTO TABLE committed_unspent_transaction_outputs (@transaction_hash, `index`, block_height, is_coinbase, amount, @locking_script) SET transaction_hash = UNHEX(@transaction_hash), locking_script = UNHEX(@locking_script)")
                .setParameter(loadFilePath)
        );
    }
}

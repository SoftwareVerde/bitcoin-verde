package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayStream;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class UtxoCommitmentLoader {
    protected static class ReadAheadUtxoInflater implements AutoCloseable {
        protected final ByteArrayStream _inputStream;
        protected final CommittedUnspentTransactionOutputInflater _committedUnspentTransactionOutputInflater;

        protected Boolean _isAtEndOfStream = false;
        protected CommittedUnspentTransactionOutput _nextCommittedUnspentTransactionOutput = null;

        protected CommittedUnspentTransactionOutput _peakCommittedUnspentTransactionOutput() {
            if (_nextCommittedUnspentTransactionOutput == null) {
                _nextCommittedUnspentTransactionOutput = _committedUnspentTransactionOutputInflater.fromByteArrayReader(_inputStream);
                if (_inputStream.didOverflow()) {
                    _nextCommittedUnspentTransactionOutput = null;
                    _isAtEndOfStream = true;
                    return null;
                }

                if (! _inputStream.hasBytes()) {
                    _isAtEndOfStream = true;
                }
            }

            return _nextCommittedUnspentTransactionOutput;
        }

        public ReadAheadUtxoInflater(final InputStream inputStream, final CommittedUnspentTransactionOutputInflater committedUnspentTransactionOutputInflater) {
            _inputStream = new ByteArrayStream(inputStream);
            _committedUnspentTransactionOutputInflater = committedUnspentTransactionOutputInflater;
        }

        public CommittedUnspentTransactionOutput peakCommittedUnspentTransactionOutput() {
            return _peakCommittedUnspentTransactionOutput();
        }

        public CommittedUnspentTransactionOutput popCommittedUnspentTransactionOutput() {
            final CommittedUnspentTransactionOutput committedUnspentTransactionOutput = _peakCommittedUnspentTransactionOutput();
            _nextCommittedUnspentTransactionOutput = null;
            return committedUnspentTransactionOutput;
        }

        @Override
        public void close() {
            _inputStream.close();
        }
    }

    public void createLoadFile(final List<File> utxoCommitmentFiles, final File outputLoadFile) throws Exception {
        final CommittedUnspentTransactionOutputInflater committedUnspentTransactionOutputInflater = new CommittedUnspentTransactionOutputInflater();

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final MutableList<ReadAheadUtxoInflater> utxoInflaters = new MutableList<ReadAheadUtxoInflater>(utxoCommitmentFiles.getCount());
        long bytesWrittenCount = 0L;
        try (
            final FileOutputStream fileOutputStream = new FileOutputStream(outputLoadFile);
        ) {
            for (final File inputFile : utxoCommitmentFiles) {
                final FileInputStream fileInputStream = new FileInputStream(inputFile);

                final ReadAheadUtxoInflater readAheadUtxoInflater = new ReadAheadUtxoInflater(fileInputStream, committedUnspentTransactionOutputInflater);
                utxoInflaters.add(readAheadUtxoInflater);
            }

            while (true) {
                final CommittedUnspentTransactionOutput committedUnspentTransactionOutput;
                {
                    ReadAheadUtxoInflater minReadAheadUtxoInflater = null;
                    TransactionOutputIdentifier minTransactionOutputIdentifier = null;
                    for (ReadAheadUtxoInflater readAheadUtxoInflater : utxoInflaters) {
                        final CommittedUnspentTransactionOutput utxo = readAheadUtxoInflater.peakCommittedUnspentTransactionOutput();
                        if (utxo == null) { continue; }

                        final int compareResult = (minTransactionOutputIdentifier != null ? CommittedUnspentTransactionOutput.compare(utxo, minTransactionOutputIdentifier) : -1);
                        if (compareResult < 0) {
                            minReadAheadUtxoInflater = readAheadUtxoInflater;
                            minTransactionOutputIdentifier = utxo.getTransactionOutputIdentifier();
                        }
                    }
                    committedUnspentTransactionOutput = ((minReadAheadUtxoInflater != null) ? minReadAheadUtxoInflater.popCommittedUnspentTransactionOutput() : null);
                }
                if (committedUnspentTransactionOutput == null) { break; }

                // transaction_hash BINARY(32) NOT NULL
                // `index` INT UNSIGNED NOT NULL
                // block_height INT UNSIGNED NOT NULL
                // is_coinbase TINYINT(1) NOT NULL DEFAULT 0
                // amount BIGINT NOT NULL
                // locking_script BLOB NOT NULL

                final String separator = "\t";

                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(committedUnspentTransactionOutput.getTransactionHash());
                stringBuilder.append(separator);
                stringBuilder.append(committedUnspentTransactionOutput.getIndex());
                stringBuilder.append(separator);
                stringBuilder.append(committedUnspentTransactionOutput.getBlockHeight());
                stringBuilder.append(separator);
                stringBuilder.append(committedUnspentTransactionOutput.isCoinbase() ? "1" : "0");
                stringBuilder.append(separator);
                stringBuilder.append(committedUnspentTransactionOutput.getAmount());
                stringBuilder.append(separator);
                stringBuilder.append(committedUnspentTransactionOutput.getLockingScript());
                stringBuilder.append(System.lineSeparator());

                final byte[] bytes = StringUtil.stringToBytes(stringBuilder.toString());
                fileOutputStream.write(bytes);
                bytesWrittenCount += bytes.length;
            }

            fileOutputStream.flush();
        }
        finally {
            for (final ReadAheadUtxoInflater readAheadUtxoInflater : utxoInflaters) {
                readAheadUtxoInflater.close();
            }
        }

        nanoTimer.stop();
        Logger.trace("Wrote " + bytesWrittenCount + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
    }

    public void loadFile(final File loadFile, final DatabaseConnection databaseConnection) throws Exception {
        final String loadFilePath = loadFile.getAbsolutePath();
        if ( (! loadFile.exists()) || (! loadFile.canRead()) ) {
            throw new Exception("Unable to access loadFile: " + loadFilePath);
        }

        databaseConnection.executeSql(
            new Query("LOAD DATA INFILE ? INTO TABLE committed_unspent_transaction_outputs (@transaction_hash, `index`, block_height, is_coinbase, amount, @locking_script) SET transaction_hash = UNHEX(@transaction_hash), locking_script = UNHEX(@locking_script)")
                .setParameter(loadFilePath)
        );
    }

    public static class CalculateMultisetHashResult {
        public final EcMultiset multisetHash;
        public final Integer utxoCount;
        public final Boolean isSorted;

        public CalculateMultisetHashResult(final EcMultiset multisetHash, final Integer utxoCount, final Boolean isSorted) {
            this.multisetHash = multisetHash;
            this.utxoCount = utxoCount;
            this.isSorted = isSorted;
        }
    }

    public CalculateMultisetHashResult calculateMultisetHash(final File file) {
        return this.calculateMultisetHash(file, true);
    }

    /**
     * Calculates the MultisetHash of the file containing Committed UTXOs.
     *  If enableMultiThread is true then the calculation will be ran in parallel across all available processors, but
     *  the entire file will be read into memory, and is therefore not well-suited for very large files.
     */
    public CalculateMultisetHashResult calculateMultisetHash(final File file, final Boolean enableMultiThread) {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final String filePath = file.getAbsolutePath();
        if ( (! file.exists()) || (! file.canRead()) ) {
            if (Util.areEqual(UtxoCommitment.EMPTY_BUCKET_NAME, file.getName())) {
                return new CalculateMultisetHashResult(new EcMultiset(), 0, true); // A non-existent file with the empty-bucket name has the hash at infinity.
            }

            Logger.debug("Unable to access loadFile: " + filePath);
            return null;
        }

        final CommittedUnspentTransactionOutputInflater utxoInflater = new CommittedUnspentTransactionOutputInflater();

        TransactionOutputIdentifier minOutputIdentifier = TransactionOutputIdentifier.COINBASE;

        int utxoCount = 0;
        final EcMultiset multisetHash = new EcMultiset();
        try (final ByteArrayStream byteArrayStream = new ByteArrayStream()) {
            final FileInputStream inputStream = new FileInputStream(file);
            byteArrayStream.appendInputStream(inputStream);

            final MutableList<ByteArray> byteArrays = new MutableList<>();
            while (true) {
                final CommittedUnspentTransactionOutput unspentTransactionOutput = utxoInflater.fromByteArrayReader(byteArrayStream);
                if (unspentTransactionOutput == null) { break; }
                // Check if the set is sorted.
                if (minOutputIdentifier != null) { // NOTE: Once/if the set is determined to be NOT sorted, this check is disabled.
                    final boolean isInSortedOrder = (CommittedUnspentTransactionOutput.compare(minOutputIdentifier, unspentTransactionOutput) <= 0);
                    if (! isInSortedOrder) {
                        minOutputIdentifier = null;
                    }
                }

                final ByteArray unspentTransactionOutputBytes = unspentTransactionOutput.getBytes();
                if (enableMultiThread) {
                    byteArrays.add(unspentTransactionOutputBytes);
                }
                else {
                    multisetHash.addItem(unspentTransactionOutputBytes);
                }

                utxoCount += 1;
            }

            if (enableMultiThread) {
                final Runtime runtime = Runtime.getRuntime();
                final int threadCount = Math.max(1, (runtime.availableProcessors() / 2));
                final int batchSize = (int) Math.ceil(((double) byteArrays.getCount()) / threadCount);
                final BatchRunner<ByteArray> batchRunner = new BatchRunner<>(batchSize, true);
                batchRunner.run(byteArrays, new BatchRunner.Batch<ByteArray>() {
                    @Override
                    public void run(final List<ByteArray> batchItems) {
                        for (final ByteArray byteArray : batchItems) {
                            multisetHash.addItem(byteArray);
                        }
                    }
                });
            }
        }
        catch (final Exception exception) {
            Logger.debug("Unable to access loadFile: " + filePath);
            return null;
        }

        nanoTimer.stop();
        Logger.trace("Calculated MultisetHash of " + file + ", containing " + utxoCount + " UTXOs, in " + nanoTimer.getMillisecondsElapsed() + "ms. " + multisetHash.getHash());

        final boolean isSorted = (minOutputIdentifier != null);
        return new CalculateMultisetHashResult(multisetHash, utxoCount, isSorted);
    }
}

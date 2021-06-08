package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.MultisetHash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

class Bucket implements AutoCloseable {
    protected static final int PAGE_SIZE = (int) (16L * ByteUtil.Unit.Binary.KIBIBYTES);

    public static class SubBucket {
        public final PublicKey publicKey;
        public final File file;
        public final Long byteCount;
        public final Integer utxoCount;

        public SubBucket(final PublicKey publicKey, final File file, final Long byteCount, final Integer utxoCount) {
            this.publicKey = publicKey;
            this.file = file;
            this.byteCount = byteCount;
            this.utxoCount = utxoCount;
        }
    }

    protected static File getEmptyFile(final String outputDirectory) {
        return new File(outputDirectory, UtxoCommitmentCore.EMPTY_BUCKET_NAME);
    }

    protected final String _outputDirectory;
    protected final Long _maxByteCountPerFile;

    protected final File _protoFile;
    protected final Integer _index;
    protected final MultisetHash _bucketMultisetHash = new MultisetHash();
    protected final MutableList<SubBucket> _subBuckets = new MutableList<>();
    protected final ConcurrentLinkedQueue<CommittedUnspentTransactionOutput> _queue;

    protected MultisetHash _multisetHash = new MultisetHash();
    protected Long _bytesWritten = 0L;
    protected Integer _utxoCount = 0;
    protected OutputStream _outputStream;

    public Bucket(final Integer index, final String outputDirectory, final Long maxByteCountPerFile) {
        _outputDirectory = outputDirectory;
        _maxByteCountPerFile = maxByteCountPerFile;

        _queue = new ConcurrentLinkedQueue<CommittedUnspentTransactionOutput>();
        _protoFile = new File(outputDirectory, "utxo-" + index + ".dat");
        _index = index;
        _outputStream = null;
    }

    public Integer getIndex() {
        return _index;
    }

    public PublicKey getPublicKey() {
        return _bucketMultisetHash.getPublicKey();
    }

    public List<SubBucket> getSubBuckets() {
        return _subBuckets;
    }

    public void createPartialUtxoCommitmentFile(final Boolean createNewStream) throws Exception {
        if (_outputStream == null) {
            _outputStream = new BufferedOutputStream(new FileOutputStream(_protoFile), PAGE_SIZE);
        }
        else {
            _outputStream.flush();
            _outputStream.close();
            _outputStream = null;
        }

        final PublicKey publicKey = _multisetHash.getPublicKey();
        final File newFile = new File(_outputDirectory, publicKey.toString());
        final boolean renameWasSuccessful = _protoFile.renameTo(newFile);
        if (! renameWasSuccessful) {
            throw new DatabaseException("Unable to create partial commit file: " + newFile.getAbsolutePath());
        }
        final SubBucket subBucket = new SubBucket(publicKey, newFile, _bytesWritten, _utxoCount);

        Logger.trace("Partial UTXO Commitment file created: " + newFile + ", " + _bytesWritten + " bytes, " + _utxoCount + " UTXOs.");

        _subBuckets.add(subBucket);
        _bucketMultisetHash.add(_multisetHash);

        if (createNewStream) {
            _outputStream = new BufferedOutputStream(new FileOutputStream(_protoFile), PAGE_SIZE);
            _multisetHash = new MultisetHash();
        }
        _bytesWritten = 0L;
        _utxoCount = 0;
    }

    public void addOutput(final CommittedUnspentTransactionOutput transactionOutput) throws Exception {
        if (_outputStream == null) {
            _outputStream = new BufferedOutputStream(new FileOutputStream(_protoFile), PAGE_SIZE);
        }

        final ByteArray byteArray = transactionOutput.getBytes();
        final int byteCount = byteArray.getByteCount();

        if (_bytesWritten + byteCount > _maxByteCountPerFile) {
            this.createPartialUtxoCommitmentFile(true);
        }

        _multisetHash.addItem(byteArray);
        for (int i = 0; i < byteCount; ++i) {
            final byte b = byteArray.getByte(i);
            _outputStream.write(b);
        }
        _bytesWritten += byteCount;
        _utxoCount += 1;
    }

    public CommittedUnspentTransactionOutput pollFromQueue() {
        return _queue.poll();
    }

    public void addToQueue(final CommittedUnspentTransactionOutput committedUnspentTransactionOutput) {
        _queue.add(committedUnspentTransactionOutput);
    }

    @Override
    public void close() throws Exception {
        if (_bytesWritten > 0) {
            this.createPartialUtxoCommitmentFile(false);
        }

        final OutputStream outputStream = _outputStream;
        _outputStream = null;
        if (outputStream != null) {
            outputStream.flush();
            outputStream.close();
        }

        if (_subBuckets.isEmpty()) {
            final MultisetHash emptyMultisetHash = new MultisetHash();
            final PublicKey publicKey = emptyMultisetHash.getPublicKey();
            final File emptyFile = Bucket.getEmptyFile(_outputDirectory);

            final SubBucket emptyBucket = new SubBucket(publicKey, emptyFile, 0L, 0);
            _subBuckets.add(emptyBucket);
        }
    }
}

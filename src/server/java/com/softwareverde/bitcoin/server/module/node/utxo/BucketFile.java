package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

class BucketFile implements AutoCloseable {
    protected static final int PAGE_SIZE = (int) (16L * ByteUtil.Unit.Binary.KIBIBYTES);

    public static class SubBucketFile {
        public final PublicKey publicKey;
        public final Integer utxoCount;
        public final Long byteCount;
        public final File file;

        public SubBucketFile(final PublicKey publicKey, final Integer utxoCount, final Long byteCount, final File file) {
            this.publicKey = publicKey;
            this.utxoCount = utxoCount;
            this.byteCount = byteCount;
            this.file = file;
        }
    }

    protected static File getEmptyFile(final String outputDirectory) {
        return new File(outputDirectory, UtxoCommitmentCore.EMPTY_BUCKET_NAME);
    }

    protected final String _outputDirectory;
    protected final Long _maxByteCountPerFile;

    protected final File _protoFile;
    protected final Integer _index;
    protected final EcMultiset _bucketMultisetHash = new EcMultiset();
    protected final MutableList<SubBucketFile> _subBuckets = new MutableList<>();
    protected final ConcurrentLinkedQueue<CommittedUnspentTransactionOutput> _queue;

    protected EcMultiset _multisetHash = new EcMultiset();
    protected Long _bytesWritten = 0L;
    protected Integer _utxoCount = 0;
    protected OutputStream _outputStream;

    public BucketFile(final Integer index, final String outputDirectory, final Long maxByteCountPerFile) {
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

    public List<SubBucketFile> getSubBuckets() {
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
        final SubBucketFile subBucket = new SubBucketFile(publicKey, _utxoCount, _bytesWritten, newFile);

        Logger.trace("Partial UTXO Commitment file created: " + newFile + ", " + _bytesWritten + " bytes, " + _utxoCount + " UTXOs.");

        _subBuckets.add(subBucket);
        _bucketMultisetHash.add(_multisetHash);

        if (createNewStream) {
            _outputStream = new BufferedOutputStream(new FileOutputStream(_protoFile), PAGE_SIZE);
            _multisetHash = new EcMultiset();
        }
        _bytesWritten = 0L;
        _utxoCount = 0;
    }

    public void addOutput(final CommittedUnspentTransactionOutput committedUnspentTransactionOutput) throws Exception {
        if (_outputStream == null) {
            _outputStream = new BufferedOutputStream(new FileOutputStream(_protoFile), PAGE_SIZE);
        }

        final ByteArray byteArray = committedUnspentTransactionOutput.getBytes();
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
            final EcMultiset emptyMultisetHash = new EcMultiset();
            final PublicKey publicKey = emptyMultisetHash.getPublicKey();
            final File emptyFile = BucketFile.getEmptyFile(_outputDirectory);

            final SubBucketFile emptyBucket = new SubBucketFile(publicKey, 0, 0L, emptyFile);
            _subBuckets.add(emptyBucket);
        }
    }
}

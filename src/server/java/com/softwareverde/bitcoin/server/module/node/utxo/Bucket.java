package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.constable.bytearray.ByteArray;
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

class Bucket {
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

    public final File protoFile;
    public final Integer index;
    public final MultisetHash bucketMultisetHash = new MultisetHash();
    public final MutableList<SubBucket> subBuckets = new MutableList<>();
    public final ConcurrentLinkedQueue<CommittedUnspentTransactionOutput> queue;
    public MultisetHash multisetHash = new MultisetHash();
    public Long bytesWritten = 0L;
    public Integer utxoCount = 0;
    public OutputStream outputStream;

    public Bucket(final Integer index, final String outputDirectory, final Long maxByteCountPerFile) {
        _outputDirectory = outputDirectory;
        _maxByteCountPerFile = maxByteCountPerFile;

        this.queue = new ConcurrentLinkedQueue<CommittedUnspentTransactionOutput>();
        this.protoFile = new File(outputDirectory, "utxo-" + index + ".dat");
        this.index = index;
        this.outputStream = null;
    }

    public void createPartialUtxoCommitmentFile(final Boolean createNewStream) throws Exception {
        if (this.outputStream == null) {
            this.outputStream = new BufferedOutputStream(new FileOutputStream(this.protoFile), PAGE_SIZE);
        }
        else {
            this.outputStream.flush();
            this.outputStream.close();
        }

        final PublicKey publicKey = this.multisetHash.getPublicKey();
        final File newFile = new File(_outputDirectory, publicKey.toString());
        final boolean renameWasSuccessful = this.protoFile.renameTo(newFile);
        if (! renameWasSuccessful) {
            throw new DatabaseException("Unable to create partial commit file: " + newFile.getAbsolutePath());
        }
        final SubBucket subBucket = new SubBucket(publicKey, newFile, this.bytesWritten, this.utxoCount);

        Logger.trace("Partial UTXO Commitment file created: " + newFile + ", " + this.bytesWritten + " bytes, " + this.utxoCount + " UTXOs.");

        this.subBuckets.add(subBucket);
        this.outputStream = (createNewStream ? new BufferedOutputStream(new FileOutputStream(this.protoFile), PAGE_SIZE) : null);
        this.bucketMultisetHash.add(this.multisetHash);
        this.multisetHash = new MultisetHash();
        this.bytesWritten = 0L;
        this.utxoCount = 0;
    }

    public void addOutput(final CommittedUnspentTransactionOutput transactionOutput) throws Exception {
        if (this.outputStream == null) {
            this.outputStream = new BufferedOutputStream(new FileOutputStream(this.protoFile), PAGE_SIZE);
        }

        final ByteArray byteArray = transactionOutput.getBytes();
        final int byteCount = byteArray.getByteCount();

        if (this.bytesWritten + byteCount > _maxByteCountPerFile) {
            this.createPartialUtxoCommitmentFile(true);
        }

        this.multisetHash.addItem(byteArray);
        for (int i = 0; i < byteCount; ++i) {
            final byte b = byteArray.getByte(i);
            this.outputStream.write(b);
        }
        this.bytesWritten += byteCount;
        this.utxoCount += 1;
    }

    public void close() throws Exception {
        if (this.bytesWritten > 0) {
            this.createPartialUtxoCommitmentFile(false);
        }

        final OutputStream outputStream = this.outputStream;
        this.outputStream = null;
        if (outputStream != null) {
            outputStream.flush();
            outputStream.close();
        }

        if (this.subBuckets.isEmpty()) {
            final MultisetHash emptyMultisetHash = new MultisetHash();
            final PublicKey publicKey = emptyMultisetHash.getPublicKey();
            final File emptyFile = Bucket.getEmptyFile(_outputDirectory);

            final SubBucket emptyBucket = new SubBucket(publicKey, emptyFile, 0L, 0);
            this.subBuckets.add(emptyBucket);
        }
    }
}

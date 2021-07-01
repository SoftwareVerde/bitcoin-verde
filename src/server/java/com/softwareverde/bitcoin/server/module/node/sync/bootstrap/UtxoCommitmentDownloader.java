package com.softwareverde.bitcoin.server.module.node.sync.bootstrap;

import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.CommitAsyncMode;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeFilter;
import com.softwareverde.bitcoin.server.module.node.store.UtxoCommitmentStore;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockPruner;
import com.softwareverde.bitcoin.server.module.node.utxo.CommittedUnspentTransactionOutput;
import com.softwareverde.bitcoin.server.module.node.utxo.CommittedUnspentTransactionOutputInflater;
import com.softwareverde.bitcoin.server.module.node.utxo.UtxoCommitmentGenerator;
import com.softwareverde.bitcoin.server.module.node.utxo.UtxoCommitmentLoader;
import com.softwareverde.bitcoin.server.module.node.utxo.UtxoDatabaseSubBucket;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayStream;
import com.softwareverde.util.timer.MultiTimer;
import com.softwareverde.util.timer.NanoTimer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UtxoCommitmentDownloader {
    protected static final Long MAX_TIMEOUT_MS = ((UtxoCommitment.MAX_BUCKET_BYTE_COUNT * 1000L ) / BitcoinNode.MIN_BYTES_PER_SECOND);

    protected static class BucketDownload {
        boolean isComplete = false;
        final AtomicInteger downloadAttemptCount = new AtomicInteger(0);
    }

    protected static class DownloadBucketResult {
        public File file;
        public EcMultiset multisetHash;
        public Integer utxoCount;
        public Boolean isSorted;
        public Boolean wasDownloaded = false;
    }

    protected static class UtxoCommit {
        public final UtxoCommitmentMetadata utxoCommitment;
        public final UtxoCommitmentBreakdown utxoCommitmentBreakdown;
        public final Map<BitcoinNode, UtxoCommitmentBreakdown> utxoCommitmentBreakdowns;

        public UtxoCommit(final UtxoCommitmentMetadata utxoCommitment, final UtxoCommitmentBreakdown utxoCommitmentBreakdown, final Map<BitcoinNode, UtxoCommitmentBreakdown> utxoCommitmentBreakdowns) {
            this.utxoCommitment = utxoCommitment;
            this.utxoCommitmentBreakdown = utxoCommitmentBreakdown;
            this.utxoCommitmentBreakdowns = utxoCommitmentBreakdowns;
        }
    }

    protected static Tuple<UtxoCommitmentBreakdown, Integer> getCountOfNodesProvidingMostCommonBreakdown(final HashMap<UtxoCommitmentBreakdown, MutableList<BitcoinNode>> utxoCommitmentBreakdowns) {
        final Tuple<UtxoCommitmentBreakdown, Integer> largestCount = new Tuple<>(null, 0);
        for (final Map.Entry<UtxoCommitmentBreakdown, MutableList<BitcoinNode>> entry : utxoCommitmentBreakdowns.entrySet()) {
            final UtxoCommitmentBreakdown utxoCommitmentBreakdown = entry.getKey();
            final List<BitcoinNode> bitcoinNodes = entry.getValue();

            final int nodeCount = bitcoinNodes.getCount();
            if (nodeCount > largestCount.second) {
                largestCount.first = utxoCommitmentBreakdown;
                largestCount.second = nodeCount;
            }
        }
        return largestCount;
    }

    protected static void sortUtxoCommitmentFile(final File file) throws IOException {
        final CommittedUnspentTransactionOutputInflater utxoInflater = new CommittedUnspentTransactionOutputInflater();
        final TreeSet<CommittedUnspentTransactionOutput> sortedSet = new TreeSet<>(new Comparator<CommittedUnspentTransactionOutput>() {
            @Override
            public int compare(final CommittedUnspentTransactionOutput committedUnspentTransactionOutput0, final CommittedUnspentTransactionOutput committedUnspentTransactionOutput1) {
                return CommittedUnspentTransactionOutput.compare(committedUnspentTransactionOutput0, committedUnspentTransactionOutput1);
            }
        });

        try (final ByteArrayStream byteArrayStream = new ByteArrayStream()) {
            final FileInputStream inputStream = new FileInputStream(file);
            byteArrayStream.appendInputStream(inputStream);

            while (true) {
                final CommittedUnspentTransactionOutput unspentTransactionOutput = utxoInflater.fromByteArrayReader(byteArrayStream);
                if (unspentTransactionOutput == null) { break; }

                sortedSet.add(unspentTransactionOutput);
            }
        }

        final int pageSize = (int) (16L * ByteUtil.Unit.Binary.KIBIBYTES);
        try (final OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file), pageSize)) {
            for (final CommittedUnspentTransactionOutput unspentTransactionOutput : sortedSet) {
                final ByteArray deflatedUtxo = unspentTransactionOutput.getBytes();
                for (final byte b : deflatedUtxo) {
                    outputStream.write(b);
                }
            }
            outputStream.flush();
        }
    }

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final List<UtxoCommitmentMetadata> _trustedUtxoCommitments;
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final UtxoCommitmentStore _utxoCommitmentStore;
    protected final UtxoCommitmentGenerator _utxoCommitmentGenerator;
    protected final UtxoCommitmentLoader _utxoCommitmentLoader = new UtxoCommitmentLoader();
    protected final AtomicBoolean _isRunning = new AtomicBoolean(false);
    protected final AtomicBoolean _hasCompleted = new AtomicBoolean(false);
    protected final BlockPruner _blockPruner;

    protected UtxoCommit _calculateUtxoCommitToDownload() {
        final ConcurrentHashMap<RequestId, Tuple<BitcoinNode, Pin>> requestPins = new ConcurrentHashMap<>();
        final ConcurrentHashMap<RequestId, List<UtxoCommitmentBreakdown>> responses = new ConcurrentHashMap<>();
        {
            final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes(new NodeFilter() {
                @Override
                public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                    final NodeFeatures nodeFeatures = bitcoinNode.getNodeFeatures();
                    return nodeFeatures.isFeatureEnabled(NodeFeatures.Feature.UTXO_COMMITMENTS_ENABLED);
                }
            });
            if (connectedNodes.isEmpty()) {
                Logger.debug("No peers available with UtxoCommitments.");
                return null;
            }

            final long maxTimeoutMs = 60000L;
            final Pin allRequestsIssuedPin = new Pin(); // Pin used to ensure all RequestIds/BitcoinNodes are defined before the callback is executed.
            final BitcoinNode.UtxoCommitmentsCallback utxoCommitmentsCallback = new BitcoinNode.UtxoCommitmentsCallback() {
                @Override
                public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<UtxoCommitmentBreakdown> utxoCommitmentBreakdowns) {
                    { // Ensure all requests are issued before responding to a callback so that the requestPins map is populated.
                        try { allRequestsIssuedPin.waitForRelease(10000L); }
                        catch (final InterruptedException exception) { /* Nothing. */ }
                    }

                    boolean allBreakdownsAreUnique = true;
                    final HashSet<UtxoCommitmentBreakdown> uniqueUtxoCommitmentBreakdowns = new HashSet<>();

                    boolean allBreakdownsAreValid = true;
                    for (final UtxoCommitmentBreakdown utxoCommitmentBreakdown : utxoCommitmentBreakdowns) {
                        if ( allBreakdownsAreValid && (! utxoCommitmentBreakdown.isValid()) ) {
                            allBreakdownsAreValid = false;
                        }

                        final boolean isUnique = uniqueUtxoCommitmentBreakdowns.add(utxoCommitmentBreakdown);
                        if (! isUnique) {
                            allBreakdownsAreUnique = false;
                        }
                    }

                    if (allBreakdownsAreValid && allBreakdownsAreUnique) {
                        responses.put(requestId, utxoCommitmentBreakdowns);
                    }

                    final Tuple<BitcoinNode, Pin> tuple = requestPins.get(requestId);
                    final Pin pin = tuple.second;
                    pin.release();
                }
            };

            for (final BitcoinNode bitcoinNode : connectedNodes) {
                final RequestId requestId = bitcoinNode.requestUtxoCommitments(utxoCommitmentsCallback);
                requestPins.put(requestId, new Tuple<>(bitcoinNode, new Pin()));
            }
            allRequestsIssuedPin.release();

            long timeoutRemainingMs = maxTimeoutMs;
            for (final Tuple<BitcoinNode, Pin> tuple : requestPins.values()) {
                final Pin pin = tuple.second;

                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();

                try { pin.waitForRelease(timeoutRemainingMs); }
                catch (final Exception exception) { break; }

                nanoTimer.stop();
                timeoutRemainingMs -= nanoTimer.getMillisecondsElapsed();
            }
        }

        final HashMap<UtxoCommitmentMetadata, HashMap<UtxoCommitmentBreakdown, MutableList<BitcoinNode>>> availableUtxoCommitmentsMap = new HashMap<>();
        for (final Map.Entry<RequestId, List<UtxoCommitmentBreakdown>> entry : responses.entrySet()) {
            final List<UtxoCommitmentBreakdown> response = entry.getValue();
            if (response == null) { continue; }

            final RequestId requestId = entry.getKey();
            final Tuple<BitcoinNode, Pin> tuple = requestPins.get(requestId);
            final BitcoinNode bitcoinNode = tuple.first;

            for (final UtxoCommitmentBreakdown utxoCommitmentBreakdown : response) {
                final UtxoCommitmentMetadata utxoCommitmentMetadata = utxoCommitmentBreakdown.commitment;

                // NOTE: UtxoCommitmentBreakdowns' buckets ignore their subBuckets when determining equality...
                final HashMap<UtxoCommitmentBreakdown, MutableList<BitcoinNode>> breakdowns = availableUtxoCommitmentsMap.getOrDefault(utxoCommitmentMetadata, new HashMap<>());

                final MutableList<BitcoinNode> bitcoinNodes = breakdowns.getOrDefault(utxoCommitmentBreakdown, new MutableList<>());
                bitcoinNodes.add(bitcoinNode);

                breakdowns.put(utxoCommitmentBreakdown, bitcoinNodes);

                availableUtxoCommitmentsMap.put(utxoCommitmentMetadata, breakdowns);
            }
        }

        // Sort the available UtxoCommitments by most availability...
        final MutableList<UtxoCommitmentMetadata> bestUtxoCommitments = new MutableList<>(0);
        bestUtxoCommitments.addAll(availableUtxoCommitmentsMap.keySet());
        bestUtxoCommitments.sort(new Comparator<UtxoCommitmentMetadata>() {
            @Override
            public int compare(final UtxoCommitmentMetadata utxoCommitmentMetadata0, final UtxoCommitmentMetadata utxoCommitmentMetadata1) {
                final HashMap<UtxoCommitmentBreakdown, MutableList<BitcoinNode>> availableUtxoCommitmentsBreakdowns0 = availableUtxoCommitmentsMap.getOrDefault(utxoCommitmentMetadata0, new HashMap<>(0));
                final HashMap<UtxoCommitmentBreakdown, MutableList<BitcoinNode>> availableUtxoCommitmentsBreakdowns1 = availableUtxoCommitmentsMap.getOrDefault(utxoCommitmentMetadata1, new HashMap<>(0));

                // Since conflicting UtxoCommitmentBreakdowns will be ignored, only select the breakdown served by the most peers for determining the availability count.
                //  Conflicting UtxoCommitmentBreakdowns are considered to likely be a malicious/defunct node serving non-canonical breakdowns.
                final int availabilityCount0 = UtxoCommitmentDownloader.getCountOfNodesProvidingMostCommonBreakdown(availableUtxoCommitmentsBreakdowns0).second;
                final int availabilityCount1 = UtxoCommitmentDownloader.getCountOfNodesProvidingMostCommonBreakdown(availableUtxoCommitmentsBreakdowns1).second;

                final int availabilityCountCompare = Integer.compare(availabilityCount1, availabilityCount0); // NOTICE: sorted in descending order...
                if (availabilityCountCompare != 0) { return availabilityCountCompare; }

                return utxoCommitmentMetadata1.blockHeight.compareTo(utxoCommitmentMetadata0.blockHeight); // NOTICE: sorted in descending order...
            }
        });

        UtxoCommitmentMetadata selectedUtxoCommitment = null;
        UtxoCommitmentBreakdown selectedUtxoCommitmentBreakdown = null;
        for (final UtxoCommitmentMetadata utxoCommitmentMetadata : bestUtxoCommitments) {
            if (! _trustedUtxoCommitments.contains(utxoCommitmentMetadata)) { continue; }

            final HashMap<UtxoCommitmentBreakdown, MutableList<BitcoinNode>> availableUtxoCommitmentsBreakdowns = availableUtxoCommitmentsMap.get(utxoCommitmentMetadata);
            final UtxoCommitmentBreakdown mostCommonUtxoCommitmentBreakdown = UtxoCommitmentDownloader.getCountOfNodesProvidingMostCommonBreakdown(availableUtxoCommitmentsBreakdowns).first;

            long totalBucketByteCount = 0L;
            boolean breakdownHasBucketsOfUnsupportedSize = false;
            final List<UtxoCommitmentBucket> utxoCommitmentBuckets = mostCommonUtxoCommitmentBreakdown.buckets;
            for (final UtxoCommitmentBucket utxoCommitmentBucket : utxoCommitmentBuckets) {
                totalBucketByteCount += utxoCommitmentBucket.getByteCount();

                if (utxoCommitmentBucket.getByteCount() > UtxoCommitment.MAX_BUCKET_BYTE_COUNT) {
                    int subBucketsBelowMaxByteCount = 0;

                    long totalSubBucketByteCount = 0L;
                    final List<MultisetBucket> subBuckets = utxoCommitmentBucket.getSubBuckets();
                    for (final MultisetBucket subBucket : subBuckets) {
                        if (subBucket.getByteCount() <= UtxoCommitment.MAX_BUCKET_BYTE_COUNT) {
                            subBucketsBelowMaxByteCount += 1;
                        }
                        totalSubBucketByteCount += subBucket.getByteCount();
                    }
                    if ( (subBucketsBelowMaxByteCount != subBuckets.getCount()) || (totalSubBucketByteCount != utxoCommitmentBucket.getByteCount()) ) {
                        breakdownHasBucketsOfUnsupportedSize = true;
                        break;
                    }
                }
            }
            if (breakdownHasBucketsOfUnsupportedSize) { continue; }
            if (utxoCommitmentMetadata.byteCount != totalBucketByteCount) { continue; }

            selectedUtxoCommitment = utxoCommitmentMetadata;
            selectedUtxoCommitmentBreakdown = mostCommonUtxoCommitmentBreakdown;
            break;
        }

        if (selectedUtxoCommitment == null) {
            Logger.debug("Unable to find satisfactory UTXO Commitment.");
            return null;
        }

        final HashMap<UtxoCommitmentBreakdown, MutableList<BitcoinNode>> availableUtxoCommitmentsBreakdowns = availableUtxoCommitmentsMap.get(selectedUtxoCommitment);
        final List<BitcoinNode> availableBitcoinNodes = availableUtxoCommitmentsBreakdowns.get(selectedUtxoCommitmentBreakdown);

        // UtxoCommitmentBreakdown equality ignores subBuckets and since subBuckets are specific to the offering BitcoinNode, then
        //  use the exact reference originally offered by the BitcoinNode (which may include specific subBucket instructions).
        final HashMap<BitcoinNode, UtxoCommitmentBreakdown> utxoCommitmentBreakdowns = new HashMap<>();
        {
            for (final Map.Entry<RequestId, List<UtxoCommitmentBreakdown>> entry : responses.entrySet()) {
                final List<UtxoCommitmentBreakdown> responseBreakdowns = entry.getValue();
                final int index = responseBreakdowns.indexOf(selectedUtxoCommitmentBreakdown); // Ignores subBucket equality...
                if (index < 0) { continue; }

                final RequestId requestId = entry.getKey();
                final BitcoinNode bitcoinNode = requestPins.get(requestId).first;
                if (availableBitcoinNodes.contains(bitcoinNode)) {
                    final UtxoCommitmentBreakdown utxoCommitmentBreakdown = responseBreakdowns.get(index); // May contain specific subBucket definitions...
                    utxoCommitmentBreakdowns.put(bitcoinNode, utxoCommitmentBreakdown);
                }
            }
        }

        return new UtxoCommit(selectedUtxoCommitment, selectedUtxoCommitmentBreakdown, utxoCommitmentBreakdowns);
    }

    protected DownloadBucketResult _downloadBucket(final BitcoinNode bitcoinNode, final MultisetBucket bucket) {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final PublicKey publicKey = bucket.getPublicKey();

        // Check if the bucket already exists and is valid...
        if (_utxoCommitmentStore.utxoCommitmentExists(publicKey)) {
            final DownloadBucketResult downloadResult = new DownloadBucketResult();
            final File file = _utxoCommitmentStore.getUtxoCommitmentFile(publicKey);
            final UtxoCommitmentLoader.CalculateMultisetHashResult calculateMultisetHashResult = _utxoCommitmentLoader.calculateMultisetHash(file);
            final EcMultiset multisetHash = calculateMultisetHashResult.multisetHash;

            downloadResult.file = file;
            downloadResult.multisetHash = multisetHash;
            downloadResult.isSorted = calculateMultisetHashResult.isSorted;
            downloadResult.utxoCount = calculateMultisetHashResult.utxoCount;

            if (Util.areEqual(publicKey, multisetHash.getPublicKey())) {
                nanoTimer.stop();

                Logger.info("Using previously downloaded " + publicKey + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                return downloadResult;
            }
            else {
                _utxoCommitmentStore.removeUtxoCommitment(publicKey);
            }
        }

        final NanoTimer downloadTimer = new NanoTimer();
        downloadTimer.start();

        // Download the bucket from the provided peer...
        Logger.debug("Downloading: " + publicKey + " from " + bitcoinNode + ".");
        final DownloadBucketResult downloadResult = new DownloadBucketResult();
        synchronized (downloadResult) {
            bitcoinNode.requestUtxoCommitment(publicKey, new BitcoinNode.DownloadUtxoCommitmentCallback() {
                @Override
                public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final ByteArray utxoCommitmentBytes) {
                    final Long expectedByteCount = bucket.getByteCount();
                    final int byteCount = utxoCommitmentBytes.getByteCount();
                    if (! Util.areEqual(bucket.getByteCount(), byteCount)) {
                        Logger.info("Discarding UtxoBucket with incorrect byte count from " + bitcoinNode + ", expected " + expectedByteCount + " bytes, found " + byteCount + ".");
                        synchronized (downloadResult) {
                            downloadResult.notifyAll();
                            return;
                        }
                    }

                    synchronized (downloadResult) { // Mark the bucket as downloaded so that the max-timeout may exclude checksum validation time.
                        downloadResult.wasDownloaded = true;
                    }

                    downloadTimer.stop();
                    Logger.trace("Downloaded " + publicKey + " (" + byteCount + " bytes) in " + downloadTimer.getMillisecondsElapsed() + "ms.");

                    final File file = _utxoCommitmentStore.storeUtxoCommitment(publicKey, utxoCommitmentBytes);
                    final UtxoCommitmentLoader.CalculateMultisetHashResult calculateMultisetHashResult = _utxoCommitmentLoader.calculateMultisetHash(file);
                    final EcMultiset multisetHash = calculateMultisetHashResult.multisetHash;
                    final Boolean isSorted = calculateMultisetHashResult.isSorted;
                    final Integer utxoCount = calculateMultisetHashResult.utxoCount;

                    synchronized (downloadResult) {
                        downloadResult.file = file;
                        downloadResult.multisetHash = multisetHash;
                        downloadResult.isSorted = isSorted;
                        downloadResult.utxoCount = utxoCount;
                        downloadResult.notifyAll();
                    }
                }

                @Override
                public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final PublicKey response) {
                    synchronized (downloadResult) {
                        downloadResult.notifyAll();
                    }
                }
            });

            try {
                int sanityCheck = 0;
                do {
                    downloadResult.wait(MAX_TIMEOUT_MS);
                    sanityCheck += 1;
                } while ( downloadResult.wasDownloaded && (sanityCheck < 128) ); // If the file was successfully downloaded, then wait indefinitely until checksum calculation completes.

                nanoTimer.stop();

                final boolean wasSuccessful = (downloadResult.file != null);
                Logger.info("Downloaded " + publicKey + " from " + bitcoinNode + " in " + nanoTimer.getMillisecondsElapsed() + "ms. success=" + wasSuccessful);

                if (! wasSuccessful) { return null; }
                return downloadResult;
            }
            catch (final InterruptedException exception) {
                final Thread thread = Thread.currentThread();
                thread.interrupt();
                return null;
            }
        }
    }

    public UtxoCommitmentDownloader(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager, final UtxoCommitmentStore utxoCommitmentStore, final UtxoCommitmentGenerator utxoCommitmentGenerator, final BlockPruner blockPruner) {
        _databaseManagerFactory = databaseManagerFactory;
        _trustedUtxoCommitments = BitcoinConstants.getUtxoCommitments();
        _bitcoinNodeManager = bitcoinNodeManager;
        _utxoCommitmentStore = utxoCommitmentStore;
        _utxoCommitmentGenerator = utxoCommitmentGenerator;
        _blockPruner = blockPruner;
    }

    public Boolean runOnceSynchronously() {
        final boolean wasNotRunning = _isRunning.compareAndSet(false, true);
        if (! wasNotRunning) { return false; }
        if (_hasCompleted.get()) { return false; }

        final UtxoCommit utxoCommit = _calculateUtxoCommitToDownload();
        if (utxoCommit == null) {
            Logger.debug("Unable to find applicable UtxoCommitment.");
            return false;
        }

        Logger.info("Downloading " + utxoCommit.utxoCommitment.blockHash + " - " + utxoCommit.utxoCommitment.publicKey + " from " + utxoCommit.utxoCommitmentBreakdowns.size() + " nodes.");

        final List<UtxoCommitmentBucket> utxoCommitmentBuckets = utxoCommit.utxoCommitmentBreakdown.buckets;
        final int utcoCommitmentBucketCount = utxoCommitmentBuckets.getCount();
        final BucketDownload[] bucketDownloads = new BucketDownload[utcoCommitmentBucketCount];
        for (int i = 0; i < utcoCommitmentBucketCount; ++i) {
            bucketDownloads[i] = new BucketDownload();
        }

        final MutableList<File> unsortedCommitmentFiles = new MutableList<>();
        final MutableList<File> utxoCommitmentFiles = new MutableList<>();
        final MutableList<UtxoDatabaseSubBucket> localUtxoCommitFiles = new MutableList<>(); // Used to store the Utxo Commitment metadata in the database for serving to new peers.

        while (true) {
            int completeCount = 0;
            for (int bucketIndex = 0; bucketIndex < utcoCommitmentBucketCount; ++bucketIndex) {
                final BucketDownload bucketDownload = bucketDownloads[bucketIndex];
                if (bucketDownload.isComplete) {
                    completeCount += 1;
                    continue;
                }

                final int downloadAttemptCount = bucketDownload.downloadAttemptCount.getAndIncrement();
                if (downloadAttemptCount > 8) {
                    Logger.info("Attempted to download bucket too many times. Aborting.");
                    break;
                }

                for (final Map.Entry<BitcoinNode, UtxoCommitmentBreakdown> entry : utxoCommit.utxoCommitmentBreakdowns.entrySet()) {
                    final BitcoinNode bitcoinNode = entry.getKey();
                    final UtxoCommitmentBreakdown bitcoinNodeUtxoCommitmentBreakdown = entry.getValue();

                    final EcMultiset calculatedMultisetHash = new EcMultiset();
                    final UtxoCommitmentBucket bitcoinNodeBucket = bitcoinNodeUtxoCommitmentBreakdown.buckets.get(bucketIndex);

                    final MutableList<File> unsortedSubBucketFiles = new MutableList<>();
                    final MutableList<File> subBucketFiles = new MutableList<>();
                    final MutableList<UtxoDatabaseSubBucket> databaseSubBuckets = new MutableList<>();
                    if (bitcoinNodeBucket.hasSubBuckets()) {
                        int subBucketIndex = 0;
                        for (final MultisetBucket subBucket : bitcoinNodeBucket.getSubBuckets()) {
                            final DownloadBucketResult downloadBucketResult = _downloadBucket(bitcoinNode, subBucket);
                            if (downloadBucketResult == null) { break; }

                            subBucketFiles.add(downloadBucketResult.file);
                            databaseSubBuckets.add(new UtxoDatabaseSubBucket(bucketIndex, subBucketIndex, subBucket.getPublicKey(), downloadBucketResult.utxoCount, subBucket.getByteCount()));
                            calculatedMultisetHash.add(downloadBucketResult.multisetHash);

                            if (! downloadBucketResult.isSorted) {
                                unsortedSubBucketFiles.add(downloadBucketResult.file);
                            }

                            subBucketIndex += 1;
                        }
                    }
                    else {
                        final DownloadBucketResult downloadBucketResult = _downloadBucket(bitcoinNode, bitcoinNodeBucket);
                        if (downloadBucketResult == null) { continue; }

                        subBucketFiles.add(downloadBucketResult.file);
                        databaseSubBuckets.add(new UtxoDatabaseSubBucket(bucketIndex, 0, bitcoinNodeBucket.getPublicKey(), downloadBucketResult.utxoCount, bitcoinNodeBucket.getByteCount()));
                        calculatedMultisetHash.add(downloadBucketResult.multisetHash);

                        if (! downloadBucketResult.isSorted) {
                            unsortedSubBucketFiles.add(downloadBucketResult.file);
                        }
                    }

                    final PublicKey expectedPublicKey = bitcoinNodeBucket.getPublicKey().compress();
                    final PublicKey calculatedPublicKey = calculatedMultisetHash.getPublicKey().compress();
                    final boolean bucketPassedIntegrityCheck = Util.areEqual(expectedPublicKey, calculatedPublicKey);
                    if (bucketPassedIntegrityCheck) {
                        bucketDownload.isComplete = true;
                        utxoCommitmentFiles.addAll(subBucketFiles);
                        localUtxoCommitFiles.addAll(databaseSubBuckets);
                        unsortedCommitmentFiles.addAll(unsortedSubBucketFiles);
                    }
                    else {
                        Logger.info("Node served invalid UtxoCommitment: " + bitcoinNode + ", expected " + expectedPublicKey + " received " + calculatedPublicKey + ".");
                        bitcoinNode.disconnect();
                        break;
                    }
                }
            }

            if (completeCount >= utcoCommitmentBucketCount) {
                final PublicKey multisetPublicKey = utxoCommit.utxoCommitment.publicKey;
                final File loadFileDestination = new File(_utxoCommitmentStore.getUtxoDataDirectory(), (multisetPublicKey + ".sql"));
                try {
                    // Sort contents of any unsorted file...
                    for (final File unsortedFile : unsortedCommitmentFiles) {
                        final NanoTimer nanoTimer = new NanoTimer();
                        nanoTimer.start();
                        UtxoCommitmentDownloader.sortUtxoCommitmentFile(unsortedFile);
                        nanoTimer.stop();
                        Logger.debug("Sorted UtxoCommitment " + unsortedFile.getName() + " in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                    }

                    final MultiTimer multiTimer = new MultiTimer();
                    multiTimer.start();
                    _utxoCommitmentLoader.createLoadFile(utxoCommitmentFiles, loadFileDestination);
                    multiTimer.mark("createLoadFile");
                    try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
                        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
                        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                        final Long blockHeight = (utxoCommit.utxoCommitment.blockHeight - 1L); // Utxo Commitments do not include the outputs of that block/blockHeight.

                        UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.lock();
                        try {
                            unspentTransactionOutputDatabaseManager.clearCommittedUtxoSet();
                            multiTimer.mark("clearCommittedUtxoSet");

                            _utxoCommitmentLoader.loadFile(loadFileDestination, databaseConnection);
                            multiTimer.mark("loadFile");

                            unspentTransactionOutputDatabaseManager.setUncommittedUnspentTransactionOutputBlockHeight(blockHeight);
                            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_databaseManagerFactory, CommitAsyncMode.BLOCK_UNTIL_COMPLETE);
                            multiTimer.mark("commitUtxoSet");

                            blockDatabaseManager.setBlockHeight(blockHeight);
                            multiTimer.mark("setBlockHeight");

                            if (_blockPruner != null) {
                                _blockPruner.setLastPrunedBlockHeight(blockHeight, databaseManager);
                            }
                            multiTimer.mark("setPrunedHeight");

                            _utxoCommitmentGenerator.storeUtxoCommitment(utxoCommit.utxoCommitment, localUtxoCommitFiles);
                            multiTimer.stop("storeDb");
                        }
                        finally {
                            UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.unlock();
                        }
                    }
                    Logger.debug("Loaded UtxoCommit: " + multiTimer);
                }
                catch (final Exception exception) {
                    Logger.debug(exception);
                }
                break;
            }
        }

        _hasCompleted.set(true);
        _isRunning.set(false);
        return true;
    }
}

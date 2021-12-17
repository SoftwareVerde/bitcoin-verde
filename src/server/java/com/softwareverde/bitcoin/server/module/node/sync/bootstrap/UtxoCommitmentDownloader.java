package com.softwareverde.bitcoin.server.module.node.sync.bootstrap;

import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentSubBucket;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.utxo.NodeSpecificUtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.CommitAsyncMode;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputVisitor;
import com.softwareverde.bitcoin.server.module.node.database.utxo.UtxoCommitmentDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeFilter;
import com.softwareverde.bitcoin.server.module.node.store.UtxoCommitmentStore;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockPruner;
import com.softwareverde.bitcoin.server.module.node.utxo.CommittedUnspentTransactionOutput;
import com.softwareverde.bitcoin.server.module.node.utxo.CommittedUnspentTransactionOutputInflater;
import com.softwareverde.bitcoin.server.module.node.utxo.UtxoCommitmentLoader;
import com.softwareverde.bitcoin.server.module.node.utxo.UtxoDatabaseSubBucket;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayStream;
import com.softwareverde.util.timer.MilliTimer;
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
        public volatile Boolean wasDownloaded = false;
        public volatile Boolean hasFinished = false;

        public Boolean wasSuccessful() {
            return (this.file != null);
        }
    }

    protected static class DownloadableUtxoCommitment {
        public final UtxoCommitmentMetadata metadata;
        public final UtxoCommitmentBreakdown breakdown; // Node-implementation-agnostic breakdown of a UtxoCommitmentBucket.
        public final Map<BitcoinNode, NodeSpecificUtxoCommitmentBreakdown> nodeBreakdowns;

        public DownloadableUtxoCommitment(final UtxoCommitmentMetadata utxoCommitmentMetadata, final UtxoCommitmentBreakdown utxoCommitmentBreakdown, final Map<BitcoinNode, NodeSpecificUtxoCommitmentBreakdown> utxoCommitmentBreakdowns) {
            this.metadata = utxoCommitmentMetadata;
            this.breakdown = utxoCommitmentBreakdown;
            this.nodeBreakdowns = utxoCommitmentBreakdowns;
        }
    }

    protected static Boolean isUtxoCommitmentBreakdownAcceptable(final NodeSpecificUtxoCommitmentBreakdown nodeSpecificUtxoCommitmentBreakdown) {
        long totalBucketByteCount = 0L;
        boolean breakdownHasBucketsOfUnsupportedSize = false;
        final UtxoCommitmentMetadata utxoCommitmentMetadata = nodeSpecificUtxoCommitmentBreakdown.getMetadata();
        final List<UtxoCommitmentBucket> utxoCommitmentBuckets = nodeSpecificUtxoCommitmentBreakdown.getBuckets();
        for (final UtxoCommitmentBucket utxoCommitmentBucket : utxoCommitmentBuckets) {
            totalBucketByteCount += utxoCommitmentBucket.getByteCount();

            if (utxoCommitmentBucket.getByteCount() > UtxoCommitment.MAX_BUCKET_BYTE_COUNT) {
                int subBucketsBelowMaxByteCount = 0;

                long totalSubBucketByteCount = 0L;
                final List<UtxoCommitmentSubBucket> subBuckets = utxoCommitmentBucket.getSubBuckets();
                for (final UtxoCommitmentSubBucket subBucket : subBuckets) {
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
        if (breakdownHasBucketsOfUnsupportedSize) { return false; }
        if (utxoCommitmentMetadata.byteCount != totalBucketByteCount) { return false; }

        return true;
    }

    protected static Integer countValidNodeSpecificBreakdowns(final Iterable<NodeSpecificUtxoCommitmentBreakdown> utxoCommitmentBreakdowns) {
        int validCount = 0;
        for (final NodeSpecificUtxoCommitmentBreakdown utxoCommitmentBreakdown : utxoCommitmentBreakdowns) {
            final Boolean isValid = UtxoCommitmentDownloader.isUtxoCommitmentBreakdownAcceptable(utxoCommitmentBreakdown);
            if (! isValid) { continue; }

            validCount += 1;
        }
        return validCount;
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
    protected final UtxoCommitmentLoader _utxoCommitmentLoader = new UtxoCommitmentLoader();
    protected final AtomicBoolean _isRunning = new AtomicBoolean(false);
    protected final AtomicBoolean _hasCompleted = new AtomicBoolean(false);
    protected final BlockPruner _blockPruner;
    protected final Long _fastSyncTimeoutMs;
    protected volatile Boolean _shouldAbort = false;

    protected List<BitcoinNode> _getBitcoinNodesWithUtxoCommitments() {
        return _bitcoinNodeManager.getNodes(new NodeFilter() {
            @Override
            public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                final NodeFeatures nodeFeatures = bitcoinNode.getNodeFeatures();
                return nodeFeatures.isFeatureEnabled(NodeFeatures.Feature.UTXO_COMMITMENTS_ENABLED);
            }
        });
    }

    protected DownloadableUtxoCommitment _calculateUtxoCommitmentToDownload() {
        final ConcurrentHashMap<RequestId, Tuple<BitcoinNode, Pin>> requestPins = new ConcurrentHashMap<>();
        final ConcurrentHashMap<RequestId, List<NodeSpecificUtxoCommitmentBreakdown>> getUtxoCommitmentsResponses = new ConcurrentHashMap<>();
        {
            final List<BitcoinNode> connectedNodes = _getBitcoinNodesWithUtxoCommitments();
            if (connectedNodes.isEmpty()) {
                Logger.debug("No peers available with UtxoCommitments.");
                return null;
            }

            final long maxTimeoutMs = 60000L;
            final Pin allRequestsIssuedPin = new Pin(); // Pin used to ensure all RequestIds/BitcoinNodes are defined before the callback is executed.
            final BitcoinNode.UtxoCommitmentsCallback utxoCommitmentsCallback = new BitcoinNode.UtxoCommitmentsCallback() {
                @Override
                public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<NodeSpecificUtxoCommitmentBreakdown> utxoCommitmentBreakdowns) {
                    { // Ensure all requests are issued before responding to a callback so that the requestPins map is populated.
                        try { allRequestsIssuedPin.waitForRelease(10000L); }
                        catch (final InterruptedException exception) { /* Nothing. */ }
                    }

                    boolean allBreakdownsAreUnique = true;
                    final HashSet<NodeSpecificUtxoCommitmentBreakdown> uniqueUtxoCommitmentBreakdowns = new HashSet<>();

                    boolean allBreakdownsAreValid = true;
                    for (final NodeSpecificUtxoCommitmentBreakdown utxoCommitmentBreakdown : utxoCommitmentBreakdowns) {
                        if ( allBreakdownsAreValid && (! utxoCommitmentBreakdown.isValid()) ) {
                            allBreakdownsAreValid = false;
                        }

                        final boolean isUnique = uniqueUtxoCommitmentBreakdowns.add(utxoCommitmentBreakdown);
                        if (! isUnique) {
                            allBreakdownsAreUnique = false;
                        }
                    }

                    if (allBreakdownsAreValid && allBreakdownsAreUnique) {
                        getUtxoCommitmentsResponses.put(requestId, utxoCommitmentBreakdowns);
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

        final HashMap<UtxoCommitmentMetadata, UtxoCommitmentBreakdown> availableBreakdowns = new HashMap<>();
        final HashMap<UtxoCommitmentMetadata, HashMap<BitcoinNode, NodeSpecificUtxoCommitmentBreakdown>> availableUtxoCommitmentBreakdownsByUtxoCommitment = new HashMap<>();
        for (final Map.Entry<RequestId, List<NodeSpecificUtxoCommitmentBreakdown>> entry : getUtxoCommitmentsResponses.entrySet()) {
            final List<NodeSpecificUtxoCommitmentBreakdown> bitcoinNodeUtxoCommitmentBreakdowns = entry.getValue();
            if (bitcoinNodeUtxoCommitmentBreakdowns == null) { continue; }

            final RequestId requestId = entry.getKey();
            final Tuple<BitcoinNode, Pin> tuple = requestPins.get(requestId);
            final BitcoinNode bitcoinNode = tuple.first;

            for (final NodeSpecificUtxoCommitmentBreakdown utxoCommitmentBreakdown : bitcoinNodeUtxoCommitmentBreakdowns) {
                final UtxoCommitmentMetadata utxoCommitmentMetadata = utxoCommitmentBreakdown.getMetadata();
                if (! availableBreakdowns.containsKey(utxoCommitmentMetadata)) {
                    availableBreakdowns.put(utxoCommitmentMetadata, utxoCommitmentBreakdown.toUtxoCommitmentBreakdown());
                }

                // NOTE: UtxoCommitmentBreakdowns' buckets ignore their subBuckets when determining equality...
                final HashMap<BitcoinNode, NodeSpecificUtxoCommitmentBreakdown> bitcoinNodeBreakdownsForUtxoCommit = availableUtxoCommitmentBreakdownsByUtxoCommitment.getOrDefault(utxoCommitmentMetadata, new HashMap<>());
                bitcoinNodeBreakdownsForUtxoCommit.put(bitcoinNode, utxoCommitmentBreakdown);

                availableUtxoCommitmentBreakdownsByUtxoCommitment.put(utxoCommitmentMetadata, bitcoinNodeBreakdownsForUtxoCommit);
            }
        }

        // Sort the available UtxoCommitments by most availability...
        final MutableList<Tuple<UtxoCommitmentMetadata, Integer>> utxoCommitmentAvailabilityCounts = new MutableList<>(0);
        for (final UtxoCommitmentMetadata utxoCommitmentMetadata : availableUtxoCommitmentBreakdownsByUtxoCommitment.keySet()) {
            if (! _trustedUtxoCommitments.contains(utxoCommitmentMetadata)) { continue; }

            final HashMap<?, NodeSpecificUtxoCommitmentBreakdown> availableUtxoCommitmentsBreakdowns = availableUtxoCommitmentBreakdownsByUtxoCommitment.get(utxoCommitmentMetadata);
            final int availabilityCount = UtxoCommitmentDownloader.countValidNodeSpecificBreakdowns(availableUtxoCommitmentsBreakdowns.values());

            final Tuple<UtxoCommitmentMetadata, Integer> availabilityCountTuple = new Tuple<>(utxoCommitmentMetadata, availabilityCount);
            utxoCommitmentAvailabilityCounts.add(availabilityCountTuple);
        }
        utxoCommitmentAvailabilityCounts.sort(new Comparator<Tuple<UtxoCommitmentMetadata, Integer>>() {
            @Override
            public int compare(final Tuple<UtxoCommitmentMetadata, Integer> tuple0, final Tuple<UtxoCommitmentMetadata, Integer> tuple1) {
                final int availabilityCount0 = tuple0.second;
                final int availabilityCount1 = tuple1.second;

                final int availabilityCountCompare = Integer.compare(availabilityCount1, availabilityCount0); // NOTICE: sorted in descending order...
                if (availabilityCountCompare != 0) { return availabilityCountCompare; }

                final UtxoCommitmentMetadata utxoCommitmentMetadata0 = tuple0.first;
                final UtxoCommitmentMetadata utxoCommitmentMetadata1 = tuple1.first;

                return utxoCommitmentMetadata1.blockHeight.compareTo(utxoCommitmentMetadata0.blockHeight); // NOTICE: sorted in descending order...
            }
        });

        if (utxoCommitmentAvailabilityCounts.isEmpty()) {
            Logger.debug("Unable to find satisfactory UTXO Commitment.");
            return null;
        }

        final UtxoCommitmentBreakdown selectedUtxoCommitmentBreakdown;
        {
            final Tuple<UtxoCommitmentMetadata, ?> availabilityCountTuple = utxoCommitmentAvailabilityCounts.get(0);
            selectedUtxoCommitmentBreakdown = availableBreakdowns.get(availabilityCountTuple.first);
        }

        final UtxoCommitmentMetadata selectedUtxoCommitmentMetadata = selectedUtxoCommitmentBreakdown.getMetadata();
        final HashMap<BitcoinNode, NodeSpecificUtxoCommitmentBreakdown> availableUtxoCommitmentsBreakdownsForUtxoCommitment = availableUtxoCommitmentBreakdownsByUtxoCommitment.get(selectedUtxoCommitmentMetadata);
        return new DownloadableUtxoCommitment(selectedUtxoCommitmentMetadata, selectedUtxoCommitmentBreakdown, availableUtxoCommitmentsBreakdownsForUtxoCommitment);
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
        bitcoinNode.requestUtxoCommitment(publicKey, new BitcoinNode.DownloadUtxoCommitmentCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final ByteArray utxoCommitmentBytes) {
                try {
                    final Long expectedByteCount = bucket.getByteCount();
                    final int byteCount = utxoCommitmentBytes.getByteCount();
                    if (! Util.areEqual(bucket.getByteCount(), byteCount)) {
                        Logger.info("Discarding UtxoBucket with incorrect byte count from " + bitcoinNode + ", expected " + expectedByteCount + " bytes, found " + byteCount + ".");
                        return;
                    }

                    downloadResult.wasDownloaded = true; // Mark the bucket as downloaded so that the max-timeout may exclude checksum validation time.

                    downloadTimer.stop();
                    Logger.trace("Downloaded " + publicKey + " (" + byteCount + " bytes) in " + downloadTimer.getMillisecondsElapsed() + "ms.");

                    final File file = _utxoCommitmentStore.storeUtxoCommitment(publicKey, utxoCommitmentBytes);
                    final UtxoCommitmentLoader.CalculateMultisetHashResult calculateMultisetHashResult = _utxoCommitmentLoader.calculateMultisetHash(file);
                    final EcMultiset multisetHash = calculateMultisetHashResult.multisetHash;
                    final Boolean isSorted = calculateMultisetHashResult.isSorted;
                    final Integer utxoCount = calculateMultisetHashResult.utxoCount;

                    downloadResult.file = file;
                    downloadResult.multisetHash = multisetHash;
                    downloadResult.isSorted = isSorted;
                    downloadResult.utxoCount = utxoCount;
                }
                finally {
                    synchronized (downloadResult) {
                        downloadResult.hasFinished = true;
                        downloadResult.notifyAll();
                    }
                }
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final PublicKey response) {
                synchronized (downloadResult) {
                    downloadResult.hasFinished = true;
                    downloadResult.notifyAll();
                }
            }
        });

        if (! bitcoinNode.isConnected()) {
            Logger.debug("Node " + bitcoinNode + " disconnected before bucket " + publicKey + " could download.");
            return null;
        }

        try {
            do {
                synchronized (downloadResult) {
                    if (! bitcoinNode.isConnected()) {
                        Logger.debug("Node " + bitcoinNode + " disconnected before bucket " + publicKey + " was downloaded.");
                        return null;
                    }

                    if (! downloadResult.hasFinished) {
                        downloadResult.wait(MAX_TIMEOUT_MS);
                    }
                    if (downloadResult.hasFinished) { break; }
                }
            } while (downloadResult.wasDownloaded); // If the file was successfully downloaded, then wait indefinitely until checksum calculation has completed.

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

    protected Boolean _hasTimedOut(final MilliTimer timer) {
        if (_shouldAbort) { return true; }
        if ( (_fastSyncTimeoutMs == null) || (_fastSyncTimeoutMs < 0L) ) { return false; }
        return (timer.getMillisecondsElapsed() > _fastSyncTimeoutMs);
    }

    protected Long _calculateTimeoutRemainingInSeconds(final MilliTimer timer) {
        if (_shouldAbort) { return 0L; }
        if ( (_fastSyncTimeoutMs == null) || (_fastSyncTimeoutMs < 0L) ) { return 0L; }
        return ((_fastSyncTimeoutMs - timer.getMillisecondsElapsed()) / 1000L);
    }

    protected DownloadableUtxoCommitment _waitForAvailableUtxoCommitment(final MilliTimer executionTimer) {
        do {
            final DownloadableUtxoCommitment downloadableUtxoCommitment = _calculateUtxoCommitmentToDownload();
            if (downloadableUtxoCommitment != null) {
                return downloadableUtxoCommitment;
            }

            final Long secondsRemaining = _calculateTimeoutRemainingInSeconds(executionTimer);
            Logger.info("Unable to find applicable UtxoCommitment; " + secondsRemaining + " until timeout.");

            for (int i = 0; i < 120; ++i) {
                if (_shouldAbort) { return null; }

                try { Thread.sleep(500L); }
                catch (final Exception exception) { break; }
            }
        } while (! _hasTimedOut(executionTimer));

        Logger.debug("Unable to find applicable UtxoCommitment. Timeout exceeded.");
        return null;
    }

    /**
     * Requests each connected node for available commitments and arbitrarily picks one node that is capable ot serving the requested UtxoCommitment.
     */
    protected Tuple<BitcoinNode, NodeSpecificUtxoCommitmentBreakdown> _findBitcoinNodeForUtxoCommitment(final UtxoCommitmentMetadata utxoCommitmentMetadata) {
        final MutableList<Tuple<BitcoinNode, NodeSpecificUtxoCommitmentBreakdown>> candidateBitcoinNodes = new MutableList<>();
        final PublicKey publicKey = utxoCommitmentMetadata.publicKey;
        final List<BitcoinNode> bitcoinNodes = _getBitcoinNodesWithUtxoCommitments();
        final MutableList<Pin> requestPins = new MutableList<>();
        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            final Pin pin = new Pin();
            requestPins.add(pin);

            bitcoinNode.requestUtxoCommitments(new BitcoinNode.UtxoCommitmentsCallback() {
                @Override
                public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<NodeSpecificUtxoCommitmentBreakdown> utxoCommitmentBreakdowns) {
                    for (final NodeSpecificUtxoCommitmentBreakdown nodeSpecificUtxoCommitmentBreakdown : utxoCommitmentBreakdowns) {
                        final Boolean isValid = UtxoCommitmentDownloader.isUtxoCommitmentBreakdownAcceptable(nodeSpecificUtxoCommitmentBreakdown);
                        if (! isValid) { continue; }

                        final UtxoCommitmentMetadata utxoCommitmentMetadata = nodeSpecificUtxoCommitmentBreakdown.getMetadata();
                        if (Util.areEqual(publicKey, utxoCommitmentMetadata.publicKey)) {
                            final Tuple<BitcoinNode, NodeSpecificUtxoCommitmentBreakdown> tuple = new Tuple<>(bitcoinNode, nodeSpecificUtxoCommitmentBreakdown);
                            synchronized (candidateBitcoinNodes) {
                                candidateBitcoinNodes.add(tuple);
                            }
                            break;
                        }
                    }

                    pin.release();
                }
            });
        }

        long waitMsRemaining = 60000L;
        for (final Pin pin : requestPins) {
            if (waitMsRemaining <= 0L) { break; }

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();

            try {
                pin.waitForRelease(waitMsRemaining);
            }
            catch (final Exception exception) {
                final Thread thread = Thread.currentThread();
                thread.interrupt();
                break;
            }

            nanoTimer.stop();
            final Double msElapsed = nanoTimer.getMillisecondsElapsed();
            waitMsRemaining -= msElapsed;
        }

        final int nodeCount = candidateBitcoinNodes.getCount();
        if (nodeCount == 0) { return null; }

        final int nodeIndex = (int) (Math.random() * nodeCount);
        return candidateBitcoinNodes.get(nodeIndex);
    }

    protected Boolean _downloadUtxoCommitmentBucket(final DownloadableUtxoCommitment downloadableUtxoCommitment, final Integer bucketIndex, final BucketDownload bucketDownload, final Container<Integer> completeCount, final MutableList<File> unsortedCommitmentFiles, final MutableList<File> utxoCommitmentFiles, final MutableList<UtxoDatabaseSubBucket> localUtxoCommitmentFiles) {
        if (_shouldAbort) { return false; }

        if (bucketDownload.isComplete) {
            completeCount.value += 1;
            return true;
        }

        final int downloadAttemptCount = bucketDownload.downloadAttemptCount.getAndIncrement();
        if (downloadAttemptCount > 8) {
            Logger.info("Attempted to download bucket too many times. Aborting.");
            return false;
        }

        final BitcoinNode selectedBitcoinNode;
        final List<UtxoCommitmentBucket> utxoCommitmentBreakdownBuckets;
        {
            final List<BitcoinNode> bitcoinNodes = new ImmutableList<>(downloadableUtxoCommitment.nodeBreakdowns.keySet());
            final int bitcoinNodeCount = bitcoinNodes.getCount();

            BitcoinNode bitcoinNode = null;
            for (int i = 0; i < bitcoinNodeCount; ++i) {
                final int fuzzedBitcoinNodeIndex = (bucketIndex + i) % bitcoinNodeCount;
                bitcoinNode = bitcoinNodes.get(fuzzedBitcoinNodeIndex);

                if (! bitcoinNode.isConnected()) {
                    bitcoinNode = null;
                    continue;
                }

                break;
            }
            if (bitcoinNode != null) {
                final NodeSpecificUtxoCommitmentBreakdown nodeSpecificUtxoCommitmentBreakdown = downloadableUtxoCommitment.nodeBreakdowns.get(bitcoinNode);
                selectedBitcoinNode = bitcoinNode;
                utxoCommitmentBreakdownBuckets = nodeSpecificUtxoCommitmentBreakdown.getBuckets();
            }
            else {
                final UtxoCommitmentMetadata utxoCommitmentMetadata = downloadableUtxoCommitment.metadata;
                final Tuple<BitcoinNode, NodeSpecificUtxoCommitmentBreakdown> tuple = _findBitcoinNodeForUtxoCommitment(utxoCommitmentMetadata);
                if (tuple == null) { return false; }

                selectedBitcoinNode = tuple.first;
                utxoCommitmentBreakdownBuckets = tuple.second.getBuckets();
            }
        }
        if (selectedBitcoinNode == null) { return false; }

        final EcMultiset calculatedMultisetHash = new EcMultiset();
        final UtxoCommitmentBucket bitcoinNodeBucket = utxoCommitmentBreakdownBuckets.get(bucketIndex);

        boolean wasDownloaded = false;
        final MutableList<File> unsortedSubBucketFiles = new MutableList<>();
        final MutableList<File> subBucketFiles = new MutableList<>();
        final MutableList<UtxoDatabaseSubBucket> databaseSubBuckets = new MutableList<>();
        if (bitcoinNodeBucket.hasSubBuckets()) {
            int subBucketIndex = 0;
            for (final MultisetBucket subBucket : bitcoinNodeBucket.getSubBuckets()) {
                final DownloadBucketResult downloadBucketResult = _downloadBucket(selectedBitcoinNode, subBucket);
                if (downloadBucketResult == null) { break; }

                wasDownloaded = true;
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
            final DownloadBucketResult downloadBucketResult = _downloadBucket(selectedBitcoinNode, bitcoinNodeBucket);
            if (downloadBucketResult != null) {
                wasDownloaded = true;
                subBucketFiles.add(downloadBucketResult.file);
                databaseSubBuckets.add(new UtxoDatabaseSubBucket(bucketIndex, 0, bitcoinNodeBucket.getPublicKey(), downloadBucketResult.utxoCount, bitcoinNodeBucket.getByteCount()));
                calculatedMultisetHash.add(downloadBucketResult.multisetHash);

                if (! downloadBucketResult.isSorted) {
                    unsortedSubBucketFiles.add(downloadBucketResult.file);
                }
            }
        }

        final PublicKey expectedPublicKey = bitcoinNodeBucket.getPublicKey().compress();

        if (! wasDownloaded) {
            Logger.info("Unable to download UtxoCommitment " + expectedPublicKey + " from " + selectedBitcoinNode + ".");
            selectedBitcoinNode.disconnect();
            return true; // Continue attempting future downloads...
        }

        final PublicKey calculatedPublicKey = calculatedMultisetHash.getPublicKey().compress();
        final boolean bucketPassedIntegrityCheck = Util.areEqual(expectedPublicKey, calculatedPublicKey);
        if (! bucketPassedIntegrityCheck) {
            Logger.info("Node served invalid UtxoCommitment: " + selectedBitcoinNode + ", expected " + expectedPublicKey + " received " + calculatedPublicKey + ".");
            selectedBitcoinNode.disconnect();
            return true; // Continue attempting future downloads...
        }

        bucketDownload.isComplete = true;
        utxoCommitmentFiles.addAll(subBucketFiles);
        localUtxoCommitmentFiles.addAll(databaseSubBuckets);
        unsortedCommitmentFiles.addAll(unsortedSubBucketFiles);
        return true;
    }

    protected Boolean _downloadUtxoCommitment(final DownloadableUtxoCommitment downloadableUtxoCommitment, final MilliTimer executionTimer, final UnspentTransactionOutputVisitor unspentTransactionOutputVisitor) {
        Logger.info("Downloading " + downloadableUtxoCommitment.metadata.blockHash + " - " + downloadableUtxoCommitment.metadata.publicKey + " from " + downloadableUtxoCommitment.nodeBreakdowns.size() + " nodes.");

        final UtxoCommitmentBreakdown utxoCommitmentBreakdown = downloadableUtxoCommitment.breakdown;
        final List<MultisetBucket> utxoCommitmentBuckets = utxoCommitmentBreakdown.getBuckets();
        final int utxoCommitmentBucketCount = utxoCommitmentBuckets.getCount();
        final BucketDownload[] bucketDownloads = new BucketDownload[utxoCommitmentBucketCount];
        for (int i = 0; i < utxoCommitmentBucketCount; ++i) {
            bucketDownloads[i] = new BucketDownload();
        }

        final MutableList<File> unsortedCommitmentFiles = new MutableList<>();
        final MutableList<File> utxoCommitmentFiles = new MutableList<>();
        final MutableList<UtxoDatabaseSubBucket> localUtxoCommitmentFiles = new MutableList<>(); // Used to store the Utxo Commitment metadata in the database for serving to new peers.

        boolean didComplete = false;
        while (executionTimer.getMillisecondsElapsed() <= _fastSyncTimeoutMs) {
            final Container<Integer> completeCount = new Container<>(0);

            for (int bucketIndex = 0; bucketIndex < utxoCommitmentBucketCount; ++bucketIndex) {
                final BucketDownload bucketDownload = bucketDownloads[bucketIndex];
                final Boolean shouldContinue = _downloadUtxoCommitmentBucket(downloadableUtxoCommitment, bucketIndex, bucketDownload, completeCount, unsortedCommitmentFiles, utxoCommitmentFiles, localUtxoCommitmentFiles);
                if (! shouldContinue) {
                    return false;
                }
            }

            if (completeCount.value >= utxoCommitmentBucketCount) {
                break;
            }
        }

        final PublicKey multisetPublicKey = downloadableUtxoCommitment.metadata.publicKey;
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
            _utxoCommitmentLoader.createLoadFile(utxoCommitmentFiles, loadFileDestination, unspentTransactionOutputVisitor);
            multiTimer.mark("createLoadFile");

            try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                final UtxoCommitmentDatabaseManager utxoCommitmentDatabaseManager = databaseManager.getUtxoCommitmentDatabaseManager();

                final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
                final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                final Long blockHeight = (downloadableUtxoCommitment.metadata.blockHeight - 1L); // Utxo Commitments do not include the outputs of that block/blockHeight.

                UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.lock();
                try {
                    final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
                    TransactionUtil.startTransaction(databaseConnection);

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

                    utxoCommitmentDatabaseManager.storeUtxoCommitment(downloadableUtxoCommitment.metadata, localUtxoCommitmentFiles);
                    multiTimer.stop("storeDb");

                    didComplete = true;

                    TransactionUtil.commitTransaction(databaseConnection);
                }
                finally {
                    UnspentTransactionOutputDatabaseManager.UTXO_WRITE_MUTEX.unlock();
                    loadFileDestination.delete();
                }
            }
            Logger.debug("Loaded UtxoCommitment: " + multiTimer);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        return didComplete;
    }

    public UtxoCommitmentDownloader(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager, final UtxoCommitmentStore utxoCommitmentStore, final BlockPruner blockPruner, final Long fastSyncTimeoutMs) {
        _databaseManagerFactory = databaseManagerFactory;
        _trustedUtxoCommitments = BitcoinConstants.getUtxoCommitments();
        _bitcoinNodeManager = bitcoinNodeManager;
        _utxoCommitmentStore = utxoCommitmentStore;
        _blockPruner = blockPruner;
        _fastSyncTimeoutMs = fastSyncTimeoutMs;
    }

    public Boolean runOnceSynchronously(final UnspentTransactionOutputVisitor unspentTransactionOutputVisitor) {
        final MilliTimer executionTimer = new MilliTimer();
        executionTimer.start();

        final boolean wasNotRunning = _isRunning.compareAndSet(false, true);
        if (! wasNotRunning) { return false; }
        if (_hasCompleted.get()) { return false; }

        while (! _hasTimedOut(executionTimer)) {
            final DownloadableUtxoCommitment downloadableUtxoCommitment = _waitForAvailableUtxoCommitment(executionTimer);
            if (downloadableUtxoCommitment == null) { return false; }

            final Boolean didComplete = _downloadUtxoCommitment(downloadableUtxoCommitment, executionTimer, unspentTransactionOutputVisitor);
            if (didComplete) {
                _hasCompleted.set(true);
                break;
            }
        }

        _isRunning.set(false);
        return true;
    }

    public void shutdown() {
        _shouldAbort = true;
    }
}

package com.softwareverde.bitcoin.server.module.node.sync.bootstrap;

import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeFilter;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.timer.NanoTimer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UtxoCommitmentDownloader {
    public static final Integer MAX_SUPPORTED_BUCKET_BYTE_COUNT = BitcoinConstants.getBlockMaxByteCount();
    protected static final Long MAX_TIMEOUT_MS = (5L * 60L * 1000L);

    protected static class BucketDownload {
        boolean isComplete = false;
        final AtomicInteger downloadAttemptCount = new AtomicInteger(0);
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

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final List<UtxoCommitmentMetadata> _trustedUtxoCommitments;
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final AtomicBoolean _isRunning = new AtomicBoolean(false);

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

                return Integer.compare(availabilityCount1, availabilityCount0); // NOTICE: sorted in descending order...
            }
        });

        UtxoCommitmentMetadata selectedUtxoCommitment = null;
        UtxoCommitmentBreakdown selectedUtxoCommitmentBreakdown = null;
        for (final UtxoCommitmentMetadata utxoCommitmentMetadata : bestUtxoCommitments) {
            if (! _trustedUtxoCommitments.contains(utxoCommitmentMetadata)) { continue; }

            final HashMap<UtxoCommitmentBreakdown, MutableList<BitcoinNode>> availableUtxoCommitmentsBreakdowns = availableUtxoCommitmentsMap.get(utxoCommitmentMetadata);
            final UtxoCommitmentBreakdown mostCommonUtxoCommitmentBreakdown = UtxoCommitmentDownloader.getCountOfNodesProvidingMostCommonBreakdown(availableUtxoCommitmentsBreakdowns).first;

            boolean breakdownHasBucketsOfUnsupportedSize = false;
            final List<UtxoCommitmentBucket> utxoCommitmentBuckets = mostCommonUtxoCommitmentBreakdown.buckets;
            for (final UtxoCommitmentBucket utxoCommitmentBucket : utxoCommitmentBuckets) {
                if (utxoCommitmentBucket.getByteCount() > MAX_SUPPORTED_BUCKET_BYTE_COUNT) {
                    int subBucketsBelowMaxByteCount = 0;

                    final List<MultisetBucket> subBuckets = utxoCommitmentBucket.getSubBuckets();
                    for (final MultisetBucket subBucket : subBuckets) {
                        if (subBucket.getByteCount() <= MAX_SUPPORTED_BUCKET_BYTE_COUNT) {
                            subBucketsBelowMaxByteCount += 1;
                        }
                    }
                    if (subBucketsBelowMaxByteCount != subBuckets.getCount()) {
                        breakdownHasBucketsOfUnsupportedSize = true;
                        break;
                    }
                }
            }
            if (breakdownHasBucketsOfUnsupportedSize) { continue; }

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

    protected Boolean _downloadBucket(final BitcoinNode bitcoinNode, final MultisetBucket bucket) {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final PublicKey publicKey = bucket.getPublicKey();

        Logger.debug("Downloading: " + publicKey + " from " + bitcoinNode + ".");
        final Container<Boolean> wasSuccessful = new Container<>(false);
        synchronized (wasSuccessful) {
            bitcoinNode.requestUtxoCommitment(publicKey, new BitcoinNode.DownloadUtxoCommitmentCallback() {
                @Override
                public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final ByteArray response) {
                    synchronized (wasSuccessful) {
                        wasSuccessful.value = true;
                        wasSuccessful.notifyAll();
                    }
                }

                @Override
                public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final PublicKey response) {
                    synchronized (wasSuccessful) {
                        wasSuccessful.value = false;
                        wasSuccessful.notifyAll();
                    }
                }

            }, RequestPriority.NORMAL);

            try {
                wasSuccessful.wait(MAX_TIMEOUT_MS);

                nanoTimer.stop();
                Logger.info("Downloaded " + publicKey + " from " + bitcoinNode + " in " + nanoTimer.getMillisecondsElapsed() + "ms. success=" + wasSuccessful.value);

                return wasSuccessful.value;
            }
            catch (final InterruptedException exception) {
                final Thread thread = Thread.currentThread();
                thread.interrupt();
                return false;
            }
        }
    }

    public UtxoCommitmentDownloader(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager) {
        _databaseManagerFactory = databaseManagerFactory;
        _trustedUtxoCommitments = BitcoinConstants.getUtxoCommitments();
        _bitcoinNodeManager = bitcoinNodeManager;
    }

    public Boolean runSynchronously() {
        final boolean wasNotRunning = _isRunning.compareAndSet(false, true);
        if (! wasNotRunning) { return false; }

        final UtxoCommit utxoCommit = _calculateUtxoCommitToDownload();
        if (utxoCommit == null) {
            Logger.debug("Unable to find applicable UtxoCommitment.");
            return false;
        }

        Logger.info("Downloading " + utxoCommit.utxoCommitment.blockHash + " - " + utxoCommit.utxoCommitment.multisetHash + " from " + utxoCommit.utxoCommitmentBreakdowns.size() + " nodes.");

        final List<UtxoCommitmentBucket> utxoCommitmentBuckets = utxoCommit.utxoCommitmentBreakdown.buckets;
        final int utcoCommitmentBucketCount = utxoCommitmentBuckets.getCount();
        final BucketDownload[] bucketDownloads = new BucketDownload[utcoCommitmentBucketCount];
        for (int i = 0; i < utcoCommitmentBucketCount; ++i) {
            bucketDownloads[i] = new BucketDownload();
        }

        while (true) {
            int completeCount = 0;
            for (int i = 0; i < utcoCommitmentBucketCount; ++i) {
                final BucketDownload bucketDownload = bucketDownloads[i];
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

                    final UtxoCommitmentBucket bitcoinNodeBucket = bitcoinNodeUtxoCommitmentBreakdown.buckets.get(i);
                    final Boolean downloadWasSuccessful;
                    if (bitcoinNodeBucket.hasSubBuckets()) {
                        boolean subBucketsWereDownloadedSuccessfully = true;
                        for (final MultisetBucket subBucket : bitcoinNodeBucket.getSubBuckets()) {
                            final Boolean subBucketDownloadWasSuccess = _downloadBucket(bitcoinNode, subBucket);
                            if (! subBucketDownloadWasSuccess) {
                                subBucketsWereDownloadedSuccessfully = false;
                                break;
                            }
                        }
                        downloadWasSuccessful = subBucketsWereDownloadedSuccessfully;
                    }
                    else {
                        downloadWasSuccessful = _downloadBucket(bitcoinNode, bitcoinNodeBucket);
                    }

                    if (downloadWasSuccessful) {
                        bucketDownload.isComplete = true;
                    }
                }
            }

            if (completeCount >= utcoCommitmentBucketCount) {
                break;
            }
        }

        _isRunning.set(false);
        return true;
    }
}

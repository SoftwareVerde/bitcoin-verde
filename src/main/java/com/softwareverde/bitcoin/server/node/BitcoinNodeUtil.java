package com.softwareverde.bitcoin.server.node;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.Util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class BitcoinNodeUtil {
    public BitcoinNodeUtil() { }

    /**
     * Returns true iff a callback was executed.
     */
    public static <T, S extends BitcoinNode.BitcoinNodeCallback> Boolean executeAndClearCallbacks(final ThreadPool threadPool, final Map<T, Set<BitcoinNode.PendingRequest<S>>> callbackMap, final Map<RequestId, FailableRequest> failableRequests, final T key, final BitcoinNode.CallbackExecutor<S> callbackExecutor) {
        synchronized (callbackMap) {
            final Set<BitcoinNode.PendingRequest<S>> pendingRequests = callbackMap.remove(key);
            if ((pendingRequests == null) || (pendingRequests.isEmpty())) { return false; }

            for (final BitcoinNode.PendingRequest<S> pendingRequest : pendingRequests) {
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        callbackExecutor.onResult(pendingRequest);
                    }
                });
                failableRequests.remove(pendingRequest.requestId);
            }
            return true;
        }
    }

    public static <T, S> void storeInMapSet(final Map<T, Set<S>> destinationMap, final T key, final S value) {
        synchronized (destinationMap) {
            Set<S> destinationSet = destinationMap.get(key);
            if (destinationSet == null) {
                destinationSet = new HashSet<S>();
                destinationMap.put(key, destinationSet);
            }
            destinationSet.add(value);
        }
    }

    public static <T, S> void storeInMapList(final Map<T, MutableList<S>> destinationList, final T key, final S value) {
        synchronized (destinationList) {
            MutableList<S> destinationSet = destinationList.get(key);
            if (destinationSet == null) {
                destinationSet = new MutableList<S>();
                destinationList.put(key, destinationSet);
            }
            destinationSet.add(value);
        }
    }

    public static <T, S extends BitcoinNode.BitcoinNodeCallback> void removeValueFromMapSet(final Map<T, Set<BitcoinNode.PendingRequest<S>>> sourceMap, final RequestId requestId) {
        synchronized (sourceMap) {
            final Iterator<Set<BitcoinNode.PendingRequest<S>>> sourceMapIterator = sourceMap.values().iterator();
            while (sourceMapIterator.hasNext()) {
                final Set<BitcoinNode.PendingRequest<S>> callbackSet = sourceMapIterator.next();
                final Iterator<BitcoinNode.PendingRequest<S>> pendingRequestSetIterator = callbackSet.iterator();
                while (pendingRequestSetIterator.hasNext()) {
                    final BitcoinNode.PendingRequest<S> pendingRequest = pendingRequestSetIterator.next();
                    if (Util.areEqual(pendingRequest.requestId, requestId)) {
                        pendingRequestSetIterator.remove();
                    }
                }

                if (callbackSet.isEmpty()) {
                    sourceMapIterator.remove();
                }
            }
        }
    }

    public static <T, U, S extends BitcoinNode.FailableBitcoinNodeRequestCallback<U, T>> void failPendingRequests(final ThreadPool threadPool, final Map<T, Set<BitcoinNode.PendingRequest<S>>> pendingRequests, final Map<RequestId, FailableRequest> failableRequests, final BitcoinNode bitcoinNode) {
        synchronized (pendingRequests) {
            for (final T key : pendingRequests.keySet()) {
                for (final BitcoinNode.PendingRequest<S> pendingRequest : pendingRequests.get(key)) {
                    final RequestId requestId = pendingRequest.requestId;
                    final S callback = pendingRequest.callback;
                    failableRequests.remove(requestId);

                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure(requestId, bitcoinNode, key);
                        }
                    });
                }
            }
            pendingRequests.clear();
        }
    }

    public static <T, U, S extends BitcoinNode.FailableBitcoinNodeRequestCallback<U, Void>> void failPendingVoidRequests(final ThreadPool threadPool, final Map<T, Set<BitcoinNode.PendingRequest<S>>> pendingRequests, final Map<RequestId, FailableRequest> failableRequests, final BitcoinNode bitcoinNode) {
        synchronized (pendingRequests) {
            for (final T key : pendingRequests.keySet()) {
                for (final BitcoinNode.PendingRequest<S> pendingRequest : pendingRequests.get(key)) {
                    final RequestId requestId = pendingRequest.requestId;
                    final S callback = pendingRequest.callback;
                    failableRequests.remove(requestId);

                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure(requestId, bitcoinNode, null);
                        }
                    });
                }
            }
            pendingRequests.clear();
        }
    }
}

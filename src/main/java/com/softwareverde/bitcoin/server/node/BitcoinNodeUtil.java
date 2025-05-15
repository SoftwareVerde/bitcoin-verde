package com.softwareverde.bitcoin.server.node;

import com.softwareverde.constable.Visitor;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.ConcurrentMutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.constable.set.Set;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.constable.set.mutable.MutableSet;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

public class BitcoinNodeUtil {
    public BitcoinNodeUtil() { }

    /**
     * Returns true iff a callback was executed.
     */
    public static <T, S extends BitcoinNode.BitcoinNodeCallback> Boolean executeAndClearCallbacks(final MutableMap<T, MutableSet<BitcoinNode.PendingRequest<S>>> callbackMap, final MutableMap<RequestId, FailableRequest> failableRequests, final T key, final BitcoinNode.CallbackExecutor<S> callbackExecutor) {
        synchronized (callbackMap) {
            final Set<BitcoinNode.PendingRequest<S>> pendingRequests = callbackMap.remove(key);
            if ((pendingRequests == null) || (pendingRequests.isEmpty())) { return false; }

            for (final BitcoinNode.PendingRequest<S> pendingRequest : pendingRequests) {
                failableRequests.remove(pendingRequest.requestId);
                callbackExecutor.onResult(pendingRequest);
            }
            return true;
        }
    }

    public static <T, S> void storeInMapSet(final MutableMap<T, MutableSet<S>> destinationMap, final T key, final S value) {
        synchronized (destinationMap) {
            MutableSet<S> destinationSet = destinationMap.get(key);
            if (destinationSet == null) {
                destinationSet = new MutableHashSet<>();
                destinationMap.put(key, destinationSet);
            }
            destinationSet.add(value);
        }
    }

    public static <T, S> void storeInMapList(final MutableMap<T, MutableList<S>> destinationList, final T key, final S value) {
        synchronized (destinationList) {
            MutableList<S> destinationSet = destinationList.get(key);
            if (destinationSet == null) {
                destinationSet = new MutableArrayList<>();
                destinationList.put(key, destinationSet);
            }
            destinationSet.add(value);
        }
    }

    public static <T, S extends BitcoinNode.BitcoinNodeCallback> void removeValueFromMapSet(final MutableMap<T, MutableSet<BitcoinNode.PendingRequest<S>>> sourceMap, final RequestId requestId) {
        synchronized (sourceMap) {
            sourceMap.mutableVisit(new MutableMap.MutableVisitor<>() {
                @Override
                public boolean run(final Tuple<T, MutableSet<BitcoinNode.PendingRequest<S>>> mapEntry) {
                    mapEntry.second.mutableVisit(new MutableSet.MutableVisitor<>() {
                        @Override
                        public boolean run(final Container<BitcoinNode.PendingRequest<S>> pendingRequestContainer) {
                            if (Util.areEqual(pendingRequestContainer.value.requestId, requestId)) {
                                pendingRequestContainer.value = null; // Remove the item.
                            }

                            return true;
                        }
                    });

                    if (mapEntry.second.isEmpty()) {
                        mapEntry.first = null; // Remove the set.
                    }

                    return true;
                }
            });
        }
    }

    public static <T, U, S extends BitcoinNode.FailableBitcoinNodeRequestCallback<U, T>> void failPendingRequests(final MutableMap<T, MutableSet<BitcoinNode.PendingRequest<S>>> pendingRequests, final ConcurrentMutableHashMap<RequestId, FailableRequest> failableRequests, final BitcoinNode bitcoinNode) {
        final MutableArrayList<Tuple<S, Tuple<RequestId, T>>> failureCallbacks = new MutableArrayList<>();

        synchronized (pendingRequests) {
            for (final T key : pendingRequests.getKeys()) {
                for (final BitcoinNode.PendingRequest<S> pendingRequest : pendingRequests.get(key)) {
                    final RequestId requestId = pendingRequest.requestId;
                    final S callback = pendingRequest.callback;
                    failableRequests.remove(requestId);
                    failureCallbacks.add(new Tuple<>(callback, new Tuple<>(requestId, key)));
                }
            }
            pendingRequests.clear();
        }

        // Execute all failure callbacks after the requests have been cleared to terminate cyclic executions.
        for (final Tuple<S, Tuple<RequestId, T>> failureCallback : failureCallbacks) {
            failureCallback.first.onFailure(failureCallback.second.first, bitcoinNode, failureCallback.second.second);
        }
    }

    public static <T, U, S extends BitcoinNode.FailableBitcoinNodeRequestCallback<U, Void>> void failPendingVoidRequests(final MutableMap<T, Set<BitcoinNode.PendingRequest<S>>> pendingRequests, final ConcurrentMutableHashMap<RequestId, FailableRequest> failableRequests, final BitcoinNode bitcoinNode) {
        final MutableArrayList<Tuple<S, RequestId>> failureCallbacks = new MutableArrayList<>();

        synchronized (pendingRequests) {
            pendingRequests.visit(new Visitor<>() {
                @Override
                public boolean run(final Tuple<T, Set<BitcoinNode.PendingRequest<S>>> entry) {
                    for (final BitcoinNode.PendingRequest<S> pendingRequest : pendingRequests.get(entry.first)) {
                        final RequestId requestId = pendingRequest.requestId;
                        final S callback = pendingRequest.callback;
                        failableRequests.remove(requestId);
                        failureCallbacks.add(new Tuple<>(callback, requestId));
                    }

                    return true;
                }
            });

            pendingRequests.clear();
        }

        // Execute all failure callbacks after the requests have been cleared to terminate cyclic executions.
        for (final Tuple<S, RequestId> failureCallback : failureCallbacks) {
            failureCallback.first.onFailure(failureCallback.second, bitcoinNode, null);
        }
    }
}

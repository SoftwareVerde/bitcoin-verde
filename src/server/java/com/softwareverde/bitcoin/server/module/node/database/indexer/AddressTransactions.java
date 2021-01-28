package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

import java.util.HashMap;
import java.util.Map;

class AddressTransactions implements Jsonable {
    public final BlockchainSegmentId blockchainSegmentId;
    public final List<TransactionId> transactionIds;
    public final Map<TransactionId, MutableList<Integer>> spentOutputs;
    public final Map<TransactionId, MutableList<Integer>> previousOutputs;

    public AddressTransactions(final BlockchainSegmentId blockchainSegmentId) {
        this.blockchainSegmentId = blockchainSegmentId;
        this.transactionIds = new ImmutableList<TransactionId>();
        this.spentOutputs = new HashMap<TransactionId, MutableList<Integer>>(0);
        this.previousOutputs = new HashMap<TransactionId, MutableList<Integer>>(0);
    }

    public AddressTransactions(final BlockchainSegmentId blockchainSegmentId, final List<TransactionId> transactionIds, final HashMap<TransactionId, MutableList<Integer>> previousOutputs, final HashMap<TransactionId, MutableList<Integer>> spentOutputs) {
        this.blockchainSegmentId = blockchainSegmentId;
        this.transactionIds = transactionIds;
        this.previousOutputs = previousOutputs;
        this.spentOutputs = spentOutputs;
    }

    @Override
    public Json toJson() {
        final Json json = new Json(false);

        json.put("blockchainSegmentId", this.blockchainSegmentId);

        { // transactionIds
            final Json transactionIds = new Json(true);
            for (final TransactionId transactionId : this.transactionIds) {
                transactionIds.add(transactionId);
            }
            json.put("transactionIds", transactionIds);
        }

        { // outputIndexes
            final Json outputIndexesJson = new Json(false);
            for (final TransactionId transactionId : this.spentOutputs.keySet()) {
                final List<Integer> indexList = this.spentOutputs.get(transactionId);
                final Json indexesJson = new Json(true);
                for (final Integer index : indexList) {
                    indexesJson.add(index);
                }
                outputIndexesJson.put(transactionId.toString(), indexesJson);
            }
            json.put("spentOutputs", outputIndexesJson);
        }

        { // previousOutputs
            final Json outputIndexesJson = new Json(false);
            for (final TransactionId transactionId : this.previousOutputs.keySet()) {
                final List<Integer> indexList = this.previousOutputs.get(transactionId);
                final Json indexesJson = new Json(true);
                for (final Integer index : indexList) {
                    indexesJson.add(index);
                }
                outputIndexesJson.put(transactionId.toString(), indexesJson);
            }
            json.put("previousOutputs", outputIndexesJson);
        }

        return json;
    }

    @Override
    public String toString() {
        final Json json = this.toJson();
        return json.toString();
    }
}

package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;


class AddressTransactions implements Jsonable {
    public final BlockchainSegmentId blockchainSegmentId;
    public final List<TransactionId> transactionIds;
    public final Map<TransactionId, ? extends List<Integer>> spentOutputs;
    public final Map<TransactionId, ? extends List<Integer>> previousOutputs;

    public AddressTransactions(final BlockchainSegmentId blockchainSegmentId) {
        this.blockchainSegmentId = blockchainSegmentId;
        this.transactionIds = new ImmutableList<>();
        this.spentOutputs = new MutableHashMap<>(0);
        this.previousOutputs = new MutableHashMap<>(0);
    }

    public AddressTransactions(final BlockchainSegmentId blockchainSegmentId, final List<TransactionId> transactionIds, final Map<TransactionId, ? extends List<Integer>> previousOutputs, final Map<TransactionId, ? extends List<Integer>> spentOutputs) {
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
            for (final TransactionId transactionId : this.spentOutputs.getKeys()) {
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
            for (final TransactionId transactionId : this.previousOutputs.getKeys()) {
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

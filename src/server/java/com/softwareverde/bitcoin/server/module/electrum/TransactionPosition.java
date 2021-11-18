package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.bitcoin.server.module.electrum.json.ElectrumJson;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.Util;

import java.util.Comparator;

class TransactionPosition implements Comparable<TransactionPosition>, Jsonable {
    public static final Comparator<TransactionPosition> COMPARATOR = new Comparator<TransactionPosition>() {
        @Override
        public int compare(final TransactionPosition transactionPosition0, final TransactionPosition transactionPosition1) {
            if (transactionPosition0.isUnconfirmedTransaction()) {
                return (transactionPosition1.isUnconfirmedTransaction() ? 0 : 1);
            }
            else if (transactionPosition1.isUnconfirmedTransaction()) {
                return -1;
            }

            final int blockHeightCompare = transactionPosition0.blockHeight.compareTo(transactionPosition1.blockHeight);
            if (blockHeightCompare != 0) { return blockHeightCompare; }
            return transactionPosition0.transactionIndex.compareTo(transactionPosition1.transactionIndex);
        }
    };

    public final Long blockHeight;
    public final Integer transactionIndex;
    public final Sha256Hash transactionHash;
    public final Boolean hasUnconfirmedInputs;
    public Long transactionFee;

    protected Long _getBlockHeight() {
        if (this.isUnconfirmedTransaction()) {
            return (this.hasUnconfirmedInputs ? -1L : 0L);
        }

        return this.blockHeight;
    }

    public TransactionPosition(final Long blockHeight, final Integer transactionIndex, final Boolean hasUnconfirmedInputs, final Sha256Hash transactionHash) {
        this.blockHeight = blockHeight;
        this.transactionIndex = transactionIndex;
        this.transactionHash = transactionHash;
        this.hasUnconfirmedInputs = hasUnconfirmedInputs;
    }

    public Boolean isUnconfirmedTransaction() {
        return (this.blockHeight == null);
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof TransactionPosition)) { return false; }
        final TransactionPosition transactionPosition = (TransactionPosition) object;

        if (! Util.areEqual(this.blockHeight, transactionPosition.blockHeight)) { return false; }
        if (! Util.areEqual(this.transactionIndex, transactionPosition.transactionIndex)) { return false; }
        return true;
    }

    @Override
    public int compareTo(final TransactionPosition transactionPosition) {
        return TransactionPosition.COMPARATOR.compare(this, transactionPosition);
    }

    @Override
    public int hashCode() {
        final Long blockHeight = _getBlockHeight();
        return Long.hashCode(blockHeight);
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();
        final String transactionHashString = this.transactionHash.toString();
        final String transactionHashLowerCaseString = transactionHashString.toLowerCase();
        final Long blockHeight = _getBlockHeight();

        stringBuilder.append(transactionHashLowerCaseString);
        stringBuilder.append(":");
        stringBuilder.append(blockHeight);
        stringBuilder.append(":");
        return stringBuilder.toString();
    }

    @Override
    public Json toJson() {
        final Long blockHeight = _getBlockHeight();
        final Long transactionFee = this.transactionFee;

        final Json json = new ElectrumJson(false);
        json.put("height", blockHeight);
        json.put("tx_hash", this.transactionHash);
        if (transactionFee != null) {
            json.put("fee", transactionFee);
        }
        return json;
    }
}

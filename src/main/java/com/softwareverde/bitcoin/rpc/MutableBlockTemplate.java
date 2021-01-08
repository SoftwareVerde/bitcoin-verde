package com.softwareverde.bitcoin.rpc;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class MutableBlockTemplate extends BlockTemplate {
    public void setBlockHeight(final Long blockHeight) {
        _blockHeight = blockHeight;
    }

    public void setBlockVersion(final Long blockVersion) {
        _blockVersion = blockVersion;
    }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _previousBlockHash = previousBlockHash;
    }

    public void setDifficulty(final Difficulty difficulty) {
        _difficulty = difficulty;
    }

    public void setCoinbaseAmount(final Long coinbaseAmount) {
        _coinbaseAmount = coinbaseAmount;
    }

    public void setNonceRange(final ByteArray nonceRange) {
        if (nonceRange.getByteCount() != 8) {
            Logger.debug("Invalid byte count for nonceRange: " + nonceRange);
        }

        _nonceRange = nonceRange;
    }

    public void setCurrentTime(final Long currentTime) {
        _currentTime = currentTime;
    }

    public void setMinimumBlockTime(final Long minimumBlockTime) {
        _minimumBlockTime = minimumBlockTime;
    }

    public void setMaxSignatureOperationCount(final Long maxSignatureOperationCount) {
        _maxSignatureOperationCount = maxSignatureOperationCount;
    }

    public void setMaxBlockByteCount(final Long maxBlockByteCount) {
        _maxBlockByteCount = maxBlockByteCount;
    }

    public void setTarget(final ByteArray target) {
        if (! Util.areEqual(Sha256Hash.BYTE_COUNT, target.getByteCount())) {
            Logger.warn("Invalid target byte count: " + target.getByteCount());
            return;
        }

        _target = target;
    }

    public void setCoinbaseAuxFlags(final String coinbaseAuxFlags) {
        _coinbaseAuxFlags = coinbaseAuxFlags;
    }

    public void setLongPollId(final String longPollId) {
        _longPollId = longPollId;
    }

    public void addTransaction(final Transaction transaction, final Long fee, final Integer signatureOperationCount) {
        final Sha256Hash transactionHash = transaction.getHash();
        _transactions.add(transaction);
        _transactionFees.put(transactionHash, fee);
        _transactionSignatureOperationCounts.put(transactionHash, signatureOperationCount);
    }

    public void addCapability(final String capability) {
        _capabilities.add(capability);
    }

    public void addMutableField(final String mutableField) {
        _mutableFields.add(mutableField);
    }
}

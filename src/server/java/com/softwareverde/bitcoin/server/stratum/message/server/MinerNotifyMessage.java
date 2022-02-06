package com.softwareverde.bitcoin.server.stratum.message.server;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.task.StratumUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;

public class MinerNotifyMessage extends RequestMessage {
    protected ByteArray _jobId;
    protected Sha256Hash _previousBlockHash;
    protected ByteArray _coinbaseTransactionHead;
    protected ByteArray _coinbaseTransactionTail;
    protected final MutableList<Sha256Hash> _littleEndianMerkleTreeBranches = new MutableList<>(0);
    protected Long _blockVersion;
    protected Difficulty _blockDifficulty;
    protected Long _blockTimestamp;
    protected Boolean _shouldAbandonOldJobs;

    public MinerNotifyMessage() {
        super(ServerCommand.NOTIFY);
    }

    public ByteArray getJobId() {
        return _jobId;
    }

    public void setJobId(final ByteArray jobId) {
        _jobId = jobId;
    }

    public Sha256Hash previousBlockHash() {
        return _previousBlockHash;
    }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _previousBlockHash = previousBlockHash;
    }

    public ByteArray getCoinbaseTransactionHead() {
        return _coinbaseTransactionHead;
    }

    public void setCoinbaseTransactionHead(final ByteArray coinbaseTransactionHead) {
        _coinbaseTransactionHead = coinbaseTransactionHead;
    }

    public ByteArray getCoinbaseTransactionTail() {
        return _coinbaseTransactionTail;
    }

    public void setCoinbaseTransactionTail(final ByteArray coinbaseTransactionTail) {
        _coinbaseTransactionTail = coinbaseTransactionTail;
    }

    public List<Sha256Hash> getLittleEndianMerkleTreeBranches() {
        return _littleEndianMerkleTreeBranches;
    }

    public void setLittleEndianMerkleTreeBranches(final List<Sha256Hash> merkleTreeBranches) {
        _littleEndianMerkleTreeBranches.clear();
        for (final Sha256Hash byteArray : merkleTreeBranches) {
            _littleEndianMerkleTreeBranches.add(byteArray);
        }
    }

    public Long getBlockVersion() {
        return _blockVersion;
    }

    public void setBlockVersion(final Long blockVersion) {
        _blockVersion = blockVersion;
    }

    public Difficulty getBlockDifficulty() {
        return _blockDifficulty;
    }

    public void setBlockDifficulty(final Difficulty blockDifficulty) {
        _blockDifficulty = blockDifficulty;
    }

    public Long getBlockTimestamp() {
        return _blockTimestamp;
    }

    public void setBlockTimestamp(final Long blockTimestamp) {
        _blockTimestamp = blockTimestamp;
    }

    public Boolean getShouldAbandonOldJobs() {
        return _shouldAbandonOldJobs;
    }

    public void setShouldAbandonOldJobs(final Boolean shouldAbandonOldJobs) {
        _shouldAbandonOldJobs = shouldAbandonOldJobs;
    }

    @Override
    protected Json _getParametersJson() {
        final Json parametersJson = new Json(true);

        parametersJson.add(_jobId);

        final Sha256Hash previousBlockHashLe = _previousBlockHash.toReversedEndian();
        final ByteArray previousBlockHashSwabbedLe = StratumUtil.swabBytes(previousBlockHashLe);
        parametersJson.add(previousBlockHashSwabbedLe);

        parametersJson.add(_coinbaseTransactionHead);
        parametersJson.add(_coinbaseTransactionTail);

        final Json partialMerkleTreeJson = new Json(true);
        for (final ByteArray sha256Hash : _littleEndianMerkleTreeBranches) {
            partialMerkleTreeJson.add(sha256Hash);
        }
        parametersJson.add(partialMerkleTreeJson);

        parametersJson.add(HexUtil.toHexString(ByteUtil.integerToBytes(_blockVersion)));
        parametersJson.add(_blockDifficulty.encode());
        parametersJson.add(HexUtil.toHexString(ByteUtil.integerToBytes(_blockTimestamp)));
        parametersJson.add(_shouldAbandonOldJobs);

        return parametersJson;
    }
}

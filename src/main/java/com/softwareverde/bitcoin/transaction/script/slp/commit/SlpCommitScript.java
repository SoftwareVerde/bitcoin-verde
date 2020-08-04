package com.softwareverde.bitcoin.transaction.script.slp.commit;

import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptType;
import com.softwareverde.constable.Constable;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface SlpCommitScript extends SlpScript, Constable<ImmutableSlpCommitScript> {
    SlpTokenId getTokenId();
    Sha256Hash getBlockHash();
    Long getBlockHeight();
    MerkleRoot getMerkleRoot();
    String getMerkleTreeUrl();

    @Override
    ImmutableSlpCommitScript asConst();
}

abstract class SlpCommitScriptCore implements SlpCommitScript {
    protected SlpTokenId _tokenId;
    protected Sha256Hash _blockHash;
    protected Long _blockHeight;
    protected MerkleRoot _merkleRoot;
    protected String _merkleTreeUrl;

    public SlpCommitScriptCore() { }

    public SlpCommitScriptCore(final SlpCommitScript slpCommitScript) {
        _tokenId = slpCommitScript.getTokenId().asConst();
        _blockHash = slpCommitScript.getBlockHash();
        _blockHeight = slpCommitScript.getBlockHeight();
        _merkleRoot = slpCommitScript.getMerkleRoot();
        _merkleTreeUrl = slpCommitScript.getMerkleTreeUrl();
    }

    @Override
    public SlpScriptType getType() {
        return SlpScriptType.COMMIT;
    }

    @Override
    public Integer getMinimumTransactionOutputCount() {
        return 1; // Requires at only the Script Output...
    }

    @Override
    public SlpTokenId getTokenId() {
        return _tokenId;
    }

    @Override
    public Sha256Hash getBlockHash() {
        return _blockHash;
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        return _merkleRoot;
    }

    @Override
    public String getMerkleTreeUrl() {
        return _merkleTreeUrl;
    }
}
package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.MultisetHash;

import java.io.File;

public class UtxoCommitmentCore implements UtxoCommitment {
    protected BlockId _blockId;
    protected Long _blockHeight = 0L;
    protected MultisetHash _multisetHash;
    protected final MutableList<File> _files = new MutableList<>();

    @Override
    public BlockId getBlockId() {
        return _blockId;
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    @Override
    public Sha256Hash getHash() {
        return _multisetHash.getHash();
    }

    @Override
    public List<File> getFiles() {
        return _files;
    }
}

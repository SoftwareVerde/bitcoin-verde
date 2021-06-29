package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;

import java.io.File;

public class UtxoCommitmentCore implements UtxoCommitment {
    protected BlockId _blockId;
    protected Long _blockHeight = 0L;
    protected EcMultiset _multiset;
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
        return _multiset.getHash();
    }

    @Override
    public PublicKey getPublicKey() {
        return _multiset.getPublicKey();
    }

    @Override
    public List<File> getFiles() {
        return _files;
    }
}

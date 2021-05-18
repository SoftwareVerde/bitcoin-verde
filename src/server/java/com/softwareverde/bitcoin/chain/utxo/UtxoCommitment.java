package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.MultisetHash;

import java.io.File;

public class UtxoCommitment {
    protected Long _blockHeight = 0L;
    protected MultisetHash _multisetHash;
    protected final MutableList<File> _files = new MutableList<>();

    public Long getBlockHeight() {
        return _blockHeight;
    }

    public MultisetHash getHash() {
        return _multisetHash;
    }

    public List<File> getFiles() {
        return _files;
    }
}

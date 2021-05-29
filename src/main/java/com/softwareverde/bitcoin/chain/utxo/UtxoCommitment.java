package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.io.File;

public interface UtxoCommitment {
    Integer BUCKET_COUNT = 128;

    BlockId getBlockId();
    Long getBlockHeight();
    Sha256Hash getHash();
    List<File> getFiles();
}

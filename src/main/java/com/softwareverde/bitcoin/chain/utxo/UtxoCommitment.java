package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.ByteUtil;

import java.io.File;

public interface UtxoCommitment {
    String EMPTY_BUCKET_NAME = "000000000000000000000000000000000000000000000000000000000000000000"; // Empty compressed PublicKey (33 bytes)
    Integer BUCKET_COUNT = 128;
    Long MAX_BUCKET_BYTE_COUNT = (32L * ByteUtil.Unit.Binary.MEBIBYTES);

    BlockId getBlockId();
    Long getBlockHeight();
    Sha256Hash getHash();
    List<File> getFiles();
}

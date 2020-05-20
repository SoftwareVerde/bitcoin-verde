package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public interface BlockDownloadRequester {
    void requestBlock(BlockHeader blockHeader);
    void requestBlock(Sha256Hash blockHash, Sha256Hash previousBlockHash);
}

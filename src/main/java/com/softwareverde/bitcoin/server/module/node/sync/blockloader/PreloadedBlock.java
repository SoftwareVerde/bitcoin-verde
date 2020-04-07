package com.softwareverde.bitcoin.server.module.node.sync.blockloader;

import com.softwareverde.bitcoin.block.Block;

public interface PreloadedBlock {
    Long getBlockHeight();
    Block getBlock();
}
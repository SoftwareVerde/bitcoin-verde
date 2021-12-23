package com.softwareverde.bitcoin.server.module.stratum.callback;

import com.softwareverde.bitcoin.block.Block;

public interface BlockFoundCallback {
    void run(Block block, String workerName);
}

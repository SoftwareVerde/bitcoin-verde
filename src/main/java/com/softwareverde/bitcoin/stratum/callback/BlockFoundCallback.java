package com.softwareverde.bitcoin.stratum.callback;

import com.softwareverde.bitcoin.block.Block;

public interface BlockFoundCallback {
    void run(Block block, String workerName);
}

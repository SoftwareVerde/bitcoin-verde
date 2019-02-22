package com.softwareverde.bitcoin.server.module.stratum.api.endpoint;

import com.softwareverde.bitcoin.block.Block;

public interface StratumDataHandler {
    Block getPrototypeBlock();
}

package com.softwareverde.bitcoin.server.stratum.task;

public interface StratumMineBlockTaskBuilderFactory {
    StratumMineBlockTaskBuilder newStratumMineBlockTaskBuilder(Integer totalExtraNonceByteCount);
}

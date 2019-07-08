package com.softwareverde.bitcoin.server.stratum.task;

public interface StratumMineBlockTaskBuilderFactory {
    ConfigurableStratumMineBlockTaskBuilder newStratumMineBlockTaskBuilder(Integer totalExtraNonceByteCount);
}

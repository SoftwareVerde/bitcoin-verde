package com.softwareverde.bitcoin.server.stratum.task;

public interface StagnantStratumMineBlockTaskBuilderFactory extends StratumMineBlockTaskBuilderFactory {
    @Override
    StagnantStratumMineBlockTaskBuilderCore newStratumMineBlockTaskBuilder(Integer totalExtraNonceByteCount);
}

package com.softwareverde.bitcoin.server.stratum.task;

public interface LiveStratumMineBlockTaskBuilderCoreFactory extends StratumMineBlockTaskBuilderFactory {
    @Override
    LiveStratumMineBlockTaskBuilderCore newStratumMineBlockTaskBuilder(Integer totalExtraNonceByteCount);
}

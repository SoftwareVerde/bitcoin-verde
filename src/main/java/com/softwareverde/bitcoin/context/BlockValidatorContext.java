package com.softwareverde.bitcoin.context;

public interface BlockValidatorContext extends BlockHeaderContext, ChainWorkContext, MedianBlockTimeContext, NetworkTimeContext, UnspentTransactionOutputContext { }

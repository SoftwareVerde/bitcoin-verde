package com.softwareverde.bitcoin.transaction.script.slp;

public interface SlpScript {
    SlpScriptType getType();
    Integer getMinimumTransactionOutputCount();
}

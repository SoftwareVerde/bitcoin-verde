package com.softwareverde.bitcoin.transaction.script.slp;

import com.softwareverde.bitcoin.transaction.script.ScriptType;

public interface SlpScript {
    ScriptType getType();
    Integer getMinimumTransactionOutputCount();
}

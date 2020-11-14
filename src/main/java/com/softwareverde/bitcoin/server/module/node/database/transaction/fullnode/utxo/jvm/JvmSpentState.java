package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm;

import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;

public class JvmSpentState implements UnspentTransactionOutputDatabaseManager.SpentState {
    public static final int IS_SPENT_FLAG       = 0x01;
    public static final int IS_FLUSHED_FLAG     = 0x01 << 1;
    public static final int FORCE_FLUSH_FLAG    = 0x01 << 2;

    public static Boolean isSpent(final int bitfield) {
        return ((bitfield & IS_SPENT_FLAG) != 0x00);
    }

    public static Boolean isFlushedToDisk(final int bitfield) {
        return ((bitfield & IS_FLUSHED_FLAG) != 0x00);
    }

    public static Boolean isFlushMandatory(final int bitfield) {
        return ((bitfield & FORCE_FLUSH_FLAG) != 0x00);
    }

    protected int _bitField;

    public JvmSpentState() {
        _bitField = 0x00;
    }

    public JvmSpentState(final int intValue) {
        _bitField = intValue;
    }

    public void initialize(final int intValue) {
        _bitField = intValue;
    }

    public void setIsSpent(final Boolean isSpent) {
        if (isSpent) {
            _bitField |= IS_SPENT_FLAG;
        }
        else {
            _bitField &= (~IS_SPENT_FLAG);
        }
    }

    public void setIsFlushedToDisk(final Boolean isFlushedToDisk) {
        if (isFlushedToDisk) {
            _bitField |= IS_FLUSHED_FLAG;
        }
        else {
            _bitField &= (~IS_FLUSHED_FLAG);
        }
    }

    public void setIsFlushMandatory(final Boolean isFlushedMandatory) {
        if (isFlushedMandatory) {
            _bitField |= FORCE_FLUSH_FLAG;
        }
        else {
            _bitField &= (~FORCE_FLUSH_FLAG);
        }
    }

    @Override
    public Boolean isSpent() {
        return JvmSpentState.isSpent(_bitField);
    }

    @Override
    public Boolean isFlushedToDisk() {
        return JvmSpentState.isFlushedToDisk(_bitField);
    }

    public Boolean isFlushMandatory() {
        return JvmSpentState.isFlushMandatory(_bitField);
    }

    public int intValue() {
        return _bitField;
    }
}
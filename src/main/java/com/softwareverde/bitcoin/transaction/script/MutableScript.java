package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;

public class MutableScript implements Script {
    protected final MutableList<Operation> _operations;
    protected Integer _cachedByteCount = null;

    protected void _calculateByteCount() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        final ByteArray bytes = scriptDeflater.toBytes(this);
        _cachedByteCount = bytes.getByteCount();
    }

    public MutableScript() {
        _operations = new MutableList<Operation>();
    }

    public MutableScript(final Script script) {
        _operations = new MutableList<Operation>(script.getOperations());
    }

    public void subScript(final int opcodeIndex) {
        for (int i = 0; i < opcodeIndex; ++i) {
            _operations.remove(0);
            _cachedByteCount = null;
        }
    }

    public void removeOperations(final Operation.Opcode opcode) {
        int i = 0;
        while (i < _operations.getSize()) {
            final Operation operation = _operations.get(i);
            final boolean shouldRemoveOperation = opcode.matchesByte(operation.getOpcodeByte());
            if (shouldRemoveOperation) {
                _operations.remove(i);
                _cachedByteCount = null;
            }
            else {
                i += 1;
            }
        }
    }

    @Override
    public Hash getHash() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        final ByteArray bytes = scriptDeflater.toBytes(this);
        final byte[] hashBytes = BitcoinUtil.ripemd160(BitcoinUtil.sha256(bytes.getBytes()));
        return MutableHash.wrap(hashBytes);
    }

    @Override
    public List<Operation> getOperations() {
        return _operations;
    }

    @Override
    public int getByteCount() {
        if (_cachedByteCount == null) {
            _calculateByteCount();
        }

        return _cachedByteCount;
    }

    @Override
    public ByteArray getBytes() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        return scriptDeflater.toBytes(this);
    }

    @Override
    public ImmutableScript asConst() {
        return new ImmutableScript(this);
    }

    @Override
    public Json toJson() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        return scriptDeflater.toJson(this);
    }

    @Override
    public String toString() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        return scriptDeflater.toString(this);
    }

    @Override
    public int hashCode() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        return scriptDeflater.toString(this).hashCode();
    }
}

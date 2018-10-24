package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.type.hash.ripemd160.MutableRipemd160Hash;
import com.softwareverde.bitcoin.type.hash.ripemd160.Ripemd160Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class MutableScript implements Script {
    protected final MutableList<Operation> _operations;
    protected Integer _cachedByteCount = null;

    protected void _calculateByteCount() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        final ByteArray bytes = scriptDeflater.toBytes(this);
        _cachedByteCount = bytes.getByteCount();
    }

    protected ByteArray _toBytes() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        return scriptDeflater.toBytes(this);
    }

    public MutableScript() {
        _operations = new MutableList<Operation>();
    }

    public MutableScript(final ByteArray bytes) {
        final ScriptInflater scriptInflater = new ScriptInflater();
        _operations = scriptInflater.getOperationList(bytes);
    }

    public MutableScript(final Script script) {
        _operations = new MutableList<Operation>(script.getOperations());
    }

    public void addOperation(final Operation operation) {
        _operations.add(operation);
    }

    public void removeOperation(final int index) {
        _operations.remove(index);
    }

    public Operation getOperation(final int index) {
        return _operations.get(index);
    }

    public void concatenateScript(final Script script) {
        for (final Operation operation : script.getOperations()) {
            _operations.add(operation);
        }
    }

    public void subScript(final int opcodeIndex) {
        for (int i = 0; i < opcodeIndex; ++i) {
            _operations.remove(0);
            _cachedByteCount = null;
        }
    }

    public void removeOperations(final Opcode opcode) {
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

    public void removePushOperations(final ByteArray byteArray) {
        int i = 0;
        while (i < _operations.getSize()) {
            final Operation operation = _operations.get(i);
            final boolean shouldRemoveOperation;
            { // Remove all push-operations containing byteArray...
                if (operation.getType() == Operation.Type.OP_PUSH) {
                    final PushOperation pushOperation = (PushOperation) operation;
                    shouldRemoveOperation = pushOperation.containsBytes(byteArray);
                }
                else {
                    shouldRemoveOperation = false;
                }
            }

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
    public Boolean isValid() {
        for (final Operation operation : _operations) {
            if (operation.getType() == Operation.Type.OP_INVALID) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Ripemd160Hash getHash() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        final ByteArray bytes = scriptDeflater.toBytes(this);
        final byte[] hashBytes = BitcoinUtil.ripemd160(BitcoinUtil.sha256(bytes.getBytes()));
        return MutableRipemd160Hash.wrap(hashBytes);
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
        return _toBytes();
    }

    @Override
    public Boolean containsNonPushOperations() {
        for (final Operation operation : _operations) {
            if (operation.getType() != PushOperation.TYPE) {
                return true;
            }
        }

        return false;
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
        return _toBytes().toString();
    }

    @Override
    public int hashCode() {
        return _toBytes().hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof Script)) { return false; }
        final Script script = (Script) object;
        return (Util.areEqual(_toBytes(), script.getBytes()));
    }


    @Override
    public int simpleHashCode() {
        return super.hashCode();
    }

    @Override
    public boolean simpleEquals(final Object object) {
        return super.equals(object);
    }
}

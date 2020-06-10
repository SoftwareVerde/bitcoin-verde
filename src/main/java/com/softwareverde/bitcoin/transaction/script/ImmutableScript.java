package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Json;
import com.softwareverde.security.hash.ripemd160.MutableRipemd160Hash;
import com.softwareverde.security.hash.ripemd160.Ripemd160Hash;
import com.softwareverde.security.util.HashUtil;
import com.softwareverde.util.Util;

public class ImmutableScript implements Script, Const {
    protected List<Operation> _cachedOperations;
    protected final ByteArray _bytes;

    protected void _requireCachedOperations() {
        if (_cachedOperations == null) {
            final ScriptInflater scriptInflater = new ScriptInflater();
            _cachedOperations = scriptInflater.getOperationList(_bytes);
        }
    }

    protected ImmutableScript() {
        _bytes = new MutableByteArray(0);
    }

    public ImmutableScript(final ByteArray bytes) {
        _bytes = bytes.asConst();
    }

    public ImmutableScript(final Script script) {
        _bytes = script.getBytes().asConst();
    }

    @Override
    public Boolean isValid() {
        _requireCachedOperations();

        for (final Operation operation : _cachedOperations) {
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
        final byte[] hashBytes = HashUtil.ripemd160(HashUtil.sha256(bytes.getBytes()));
        return MutableRipemd160Hash.wrap(hashBytes);
    }

    @Override
    public List<Operation> getOperations() {
        _requireCachedOperations();

        return _cachedOperations;
    }

    @Override
    public int getByteCount() {
        return _bytes.getByteCount();
    }

    @Override
    public ByteArray getBytes() {
        return _bytes;
    }

    @Override
    public Boolean containsNonPushOperations() {
        _requireCachedOperations();

        for (final Operation operation : _cachedOperations) {
            if (operation.getType() != PushOperation.TYPE) {
                return true;
            }
        }

        return false;
    }

    @Override
    public ImmutableScript asConst() {
        return this;
    }

    @Override
    public Json toJson() {
        final ScriptDeflater scriptDeflater = new ScriptDeflater();
        return scriptDeflater.toJson(this);
    }

    @Override
    public String toString() {
        return _bytes.toString();
    }

    @Override
    public int hashCode() {
        return _bytes.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof Script)) { return false; }
        final Script script = (Script) object;
        return (Util.areEqual(_bytes, script.getBytes()));
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

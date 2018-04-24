package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.type.hash.ripemd160.MutableRipemd160Hash;
import com.softwareverde.bitcoin.type.hash.ripemd160.Ripemd160Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Json;

public class ImmutableScript implements Script, Const {
    protected List<Operation> _cachedOperations;
    protected final ByteArray _bytes;

    protected void _requireCachedOperations() {
        if (_cachedOperations == null) {
            final ScriptInflater scriptInflater = new ScriptInflater();
            _cachedOperations = scriptInflater._getOperationList(_bytes);
        }
    }

    protected ImmutableScript() {
        _bytes = new MutableByteArray(0);
    }

    public ImmutableScript(final byte[] bytes) {
        _bytes = new ImmutableByteArray(bytes);
    }

    public ImmutableScript(final Script script) {
        _bytes = script.getBytes().asConst();
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
}

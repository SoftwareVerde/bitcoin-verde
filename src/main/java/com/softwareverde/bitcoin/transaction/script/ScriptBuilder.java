package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.address.AddressType;
import com.softwareverde.bitcoin.address.ParsedAddress;
import com.softwareverde.bitcoin.address.TypedAddress;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.ComparisonOperation;
import com.softwareverde.bitcoin.transaction.script.opcode.CryptographicOperation;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.ripemd160.Ripemd160Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class ScriptBuilder {
    protected static LockingScript _createPayToAddressScript(final Address address) {
        if (address == null) { return null; }

        // TODO: Refactor to use ScriptBuilder (i.e. implement ScriptBuilder.pushOperation())...
        final byte[] addressBytes = address.getBytes();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(Opcode.COPY_1ST.getValue());
        byteArrayBuilder.appendByte(Opcode.SHA_256_THEN_RIPEMD_160.getValue());
        byteArrayBuilder.appendByte((byte) addressBytes.length);
        byteArrayBuilder.appendBytes(addressBytes, Endian.BIG);
        byteArrayBuilder.appendByte(Opcode.IS_EQUAL_THEN_VERIFY.getValue());
        byteArrayBuilder.appendByte(Opcode.CHECK_SIGNATURE.getValue());

        final ScriptInflater scriptInflater = new ScriptInflater();
        return LockingScript.castFrom(scriptInflater.fromBytes(byteArrayBuilder.build()));
    }

    // NOTE: Also known as payToPublicKeyHash (or P2PKH)...
    public static LockingScript payToAddress(final String base58Address) {
        final AddressInflater addressInflater = new AddressInflater();
        final ParsedAddress parsedAddress = addressInflater.fromBase58Check(base58Address);
        final Address address = (parsedAddress != null ? parsedAddress.getBytes() : null);
        return _createPayToAddressScript(address);
    }
    public static LockingScript payToAddress(final Address base58Address) {
        return _createPayToAddressScript(base58Address);
    }
    public static LockingScript payToAddress(final ParsedAddress base58Address) {
        final Address address = (base58Address != null ? base58Address.getBytes() : null);
        return _createPayToAddressScript(address);
    }

    public static UnlockingScript unlockPayToAddress(final ScriptSignature signature, final PublicKey publicKey) {
        final ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder.pushSignature(signature);
        scriptBuilder.pushBytes(publicKey);
        return scriptBuilder.buildUnlockingScript();
    }

    public static LockingScript payToScriptHash(final Script payToScript) {
        final Ripemd160Hash scriptHash = HashUtil.ripemd160(HashUtil.sha256(payToScript.getBytes()));
        return ScriptBuilder.payToScriptHash(scriptHash);
    }
    public static LockingScript payToScriptHash(final Ripemd160Hash scriptHash) {
        final ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder.pushOperation(CryptographicOperation.SHA_256_THEN_RIPEMD_160);
        scriptBuilder.pushOperation(PushOperation.pushBytes(scriptHash));
        scriptBuilder.pushOperation(ComparisonOperation.IS_EQUAL);
        return scriptBuilder.buildLockingScript();
    }
    public static LockingScript payToScriptHash(final Sha256Hash scriptHash) {
        final ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder.pushOperation(CryptographicOperation.DOUBLE_SHA_256);
        scriptBuilder.pushOperation(PushOperation.pushBytes(scriptHash));
        scriptBuilder.pushOperation(ComparisonOperation.IS_EQUAL);
        return scriptBuilder.buildLockingScript();
    }

    /**
     * Reverse engineer an Address's LockingScript to compute its hash...
     */
    public static Sha256Hash computeScriptHash(final TypedAddress parsedAddress) {
        return ScriptBuilder.computeScriptHash(parsedAddress.getType(), parsedAddress.getBytes());
    }

    public static Sha256Hash computeScriptHash(final AddressType addressType, final Address address) {
        final LockingScript lockingScript;
        if (addressType == AddressType.P2SH) {
            if (address.getByteCount() == Sha256Hash.BYTE_COUNT) {
                final Sha256Hash payToScriptHash = Sha256Hash.wrap(address.getBytes());
                lockingScript = ScriptBuilder.payToScriptHash(payToScriptHash);
            }
            else {
                final Ripemd160Hash payToScriptHash = Ripemd160Hash.wrap(address.getBytes());
                lockingScript = ScriptBuilder.payToScriptHash(payToScriptHash);
            }
        }
        else {
            lockingScript = ScriptBuilder.payToAddress(address);
        }
        return HashUtil.sha256(lockingScript.getBytes());
    }

    public static Sha256Hash computeScriptHash(final LockingScript lockingScript) {
        if (lockingScript == null) { return null; }

        final ByteArray lockingScriptBytes = lockingScript.getBytes();
        return HashUtil.sha256(lockingScriptBytes);
    }

    protected void _pushBytes(final ByteArray bytes) {
        final int dataByteCount = bytes.getByteCount();

        if (dataByteCount == 0) {
            // Nothing.
        }
        else if (dataByteCount <= Opcode.PUSH_DATA.getMaxValue()) {
            _byteArrayBuilder.appendByte((byte) ((int) dataByteCount));
            _byteArrayBuilder.appendBytes(bytes, Endian.BIG);
        }
        else if (dataByteCount <= 0xFFL) {
            _byteArrayBuilder.appendByte(Opcode.PUSH_DATA_BYTE.getValue());
            _byteArrayBuilder.appendByte((byte) ((int) dataByteCount));
            _byteArrayBuilder.appendBytes(bytes, Endian.BIG);
        }
        else if (dataByteCount <= 0xFFFFL) {
            _byteArrayBuilder.appendByte(Opcode.PUSH_DATA_SHORT.getValue());
            final byte[] dataDataByteCountBytes = ByteUtil.integerToBytes(dataByteCount);
            _byteArrayBuilder.appendBytes(new byte[] { dataDataByteCountBytes[3], dataDataByteCountBytes[4] }, Endian.LITTLE);
            _byteArrayBuilder.appendBytes(bytes, Endian.BIG);
        }
        else {
            _byteArrayBuilder.appendByte(Opcode.PUSH_DATA_INTEGER.getValue());
            _byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(dataByteCount), Endian.LITTLE);
            _byteArrayBuilder.appendBytes(bytes, Endian.BIG);
        }
    }

    protected final ByteArrayBuilder _byteArrayBuilder = new ByteArrayBuilder();

    public ScriptBuilder pushString(final String stringData) {
        final int stringDataByteCount = stringData.length();

        if (stringDataByteCount == 0) {
            // Nothing.
        }
        else if (stringDataByteCount <= Opcode.PUSH_DATA.getMaxValue()) {
            _byteArrayBuilder.appendByte((byte) ((int) stringDataByteCount));
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }
        else if (stringDataByteCount <= 0xFFL) {
            _byteArrayBuilder.appendByte(Opcode.PUSH_DATA_BYTE.getValue());
            _byteArrayBuilder.appendByte((byte) ((int) stringDataByteCount));
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }
        else if (stringDataByteCount <= 0xFFFFL) {
            _byteArrayBuilder.appendByte(Opcode.PUSH_DATA_SHORT.getValue());
            final byte[] stringDataByteCountBytes = ByteUtil.integerToBytes(stringDataByteCount);
            _byteArrayBuilder.appendBytes(new byte[] { stringDataByteCountBytes[3], stringDataByteCountBytes[4] }, Endian.LITTLE);
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }
        else {
            _byteArrayBuilder.appendByte(Opcode.PUSH_DATA_INTEGER.getValue());
            _byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(stringDataByteCount), Endian.LITTLE);
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }

        return this;
    }

    /**
     * Pushes the provided value on the stack as a number.  The number is encoded as the shortest possible encoding.
     */
    public ScriptBuilder pushInteger(final Long longValue) {
        final Value value = Value.fromInteger(longValue);
        _pushBytes(MutableByteArray.wrap(value.getBytes()));

        return this;
    }

    public ScriptBuilder pushBytes(final ByteArray bytes) {
        _pushBytes(bytes);

        return this;
    }

    public ScriptBuilder pushSignature(final ScriptSignature scriptSignature) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(scriptSignature.getSignature().encode());
        byteArrayBuilder.appendByte(scriptSignature.getHashType().toByte());
        _pushBytes(MutableByteArray.wrap(byteArrayBuilder.build()));

        return this;
    }

    public ScriptBuilder pushOperation(final Operation operation) {
        _byteArrayBuilder.appendBytes(operation.getBytes());

        return this;
    }

    public Script build() {
        final ScriptInflater scriptInflater = new ScriptInflater();
        return scriptInflater.fromBytes(_byteArrayBuilder.build());
    }

    public UnlockingScript buildUnlockingScript() {
        final ScriptInflater scriptInflater = new ScriptInflater();
        return UnlockingScript.castFrom(scriptInflater.fromBytes(_byteArrayBuilder.build()));
    }

    public LockingScript buildLockingScript() {
        final ScriptInflater scriptInflater = new ScriptInflater();
        return LockingScript.castFrom(scriptInflater.fromBytes(_byteArrayBuilder.build()));
    }
}

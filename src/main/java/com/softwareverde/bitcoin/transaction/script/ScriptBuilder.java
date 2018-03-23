package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.StringUtil;

public class ScriptBuilder {

    protected static LockingScript _payToAddress(final Address address) {
        final byte[] addressBytes = address.getBytes();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(Operation.SubType.COPY_1ST.getValue());
        byteArrayBuilder.appendByte(Operation.SubType.SHA_256_THEN_RIPEMD_160.getValue());
        byteArrayBuilder.appendByte((byte) addressBytes.length);
        byteArrayBuilder.appendBytes(addressBytes, Endian.BIG);
        byteArrayBuilder.appendByte(Operation.SubType.IS_EQUAL_THEN_VERIFY.getValue());
        byteArrayBuilder.appendByte(Operation.SubType.CHECK_SIGNATURE.getValue());
        return new ImmutableLockingScript(byteArrayBuilder.build());
    }

    // NOTE: Also known as payToPublicKeyHash (or P2PKH)...
    public static LockingScript payToAddress(final String base58Address) {
        return _payToAddress(Address.fromBase58Check(base58Address));
    }
    public static LockingScript payToAddress(final Address base58Address) {
        return _payToAddress(base58Address);
    }

    public static UnlockingScript unlockPayToAddress(final ScriptSignature signature, final PublicKey publicKey) {
        final ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder.pushSignature(signature);
        scriptBuilder.pushBytes(publicKey);
        return scriptBuilder.buildUnlockingScript();
    }

    protected void _pushBytes(final ByteArray bytes) {
        final Integer dataByteCount = bytes.getByteCount();

        if (dataByteCount == 0) {
            // Nothing.
        }
        else if (dataByteCount <= Operation.SubType.PUSH_DATA.getMaxValue()) {
            _byteArrayBuilder.appendByte((byte) (dataByteCount.intValue()));
            _byteArrayBuilder.appendBytes(bytes, Endian.BIG);
        }
        else if (dataByteCount <= 0xFFL) {
            _byteArrayBuilder.appendByte(Operation.SubType.PUSH_DATA_BYTE.getValue());
            _byteArrayBuilder.appendByte((byte) (dataByteCount.intValue()));
            _byteArrayBuilder.appendBytes(bytes, Endian.BIG);
        }
        else if (dataByteCount <= 0xFFFFL) {
            _byteArrayBuilder.appendByte(Operation.SubType.PUSH_DATA_SHORT.getValue());
            final byte[] stringDataByteCountBytes = ByteUtil.integerToBytes(dataByteCount);
            _byteArrayBuilder.appendBytes(new byte[] { stringDataByteCountBytes[3], stringDataByteCountBytes[4] }, Endian.LITTLE);
            _byteArrayBuilder.appendBytes(bytes, Endian.BIG);
        }
        else {
            _byteArrayBuilder.appendByte(Operation.SubType.PUSH_DATA_INTEGER.getValue());
            _byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(dataByteCount), Endian.LITTLE);
            _byteArrayBuilder.appendBytes(bytes, Endian.BIG);
        }
    }

    protected final ByteArrayBuilder _byteArrayBuilder = new ByteArrayBuilder();

    public ScriptBuilder pushString(final String stringData) {

        final Integer stringDataByteCount = stringData.length();

        if (stringDataByteCount == 0) {
            // Nothing.
        }
        else if (stringDataByteCount <= Operation.SubType.PUSH_DATA.getMaxValue()) {
            _byteArrayBuilder.appendByte((byte) (stringDataByteCount.intValue()));
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }
        else if (stringDataByteCount <= 0xFFL) {
            _byteArrayBuilder.appendByte(Operation.SubType.PUSH_DATA_BYTE.getValue());
            _byteArrayBuilder.appendByte((byte) (stringDataByteCount.intValue()));
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }
        else if (stringDataByteCount <= 0xFFFFL) {
            _byteArrayBuilder.appendByte(Operation.SubType.PUSH_DATA_SHORT.getValue());
            final byte[] stringDataByteCountBytes = ByteUtil.integerToBytes(stringDataByteCount);
            _byteArrayBuilder.appendBytes(new byte[] { stringDataByteCountBytes[3], stringDataByteCountBytes[4] }, Endian.LITTLE);
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }
        else {
            _byteArrayBuilder.appendByte(Operation.SubType.PUSH_DATA_INTEGER.getValue());
            _byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(stringDataByteCount), Endian.LITTLE);
            _byteArrayBuilder.appendBytes(StringUtil.stringToBytes(stringData), Endian.BIG);
        }

        return this;
    }

    public ScriptBuilder pushBytes(final ByteArray bytes) {
        _pushBytes(bytes);

        return this;
    }

    public ScriptBuilder pushSignature(final ScriptSignature scriptSignature) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(scriptSignature.getSignature().encodeAsDer());
        byteArrayBuilder.appendByte(scriptSignature.getHashType().getValue());
        _pushBytes(MutableByteArray.wrap(byteArrayBuilder.build()));

        return this;
    }

    public Script build() {
        return new ImmutableScript(_byteArrayBuilder.build());
    }

    public UnlockingScript buildUnlockingScript() {
        return new ImmutableUnlockingScript(_byteArrayBuilder.build());
    }

    public LockingScript buildLockingScript() {
        return new ImmutableLockingScript(_byteArrayBuilder.build());
    }
}

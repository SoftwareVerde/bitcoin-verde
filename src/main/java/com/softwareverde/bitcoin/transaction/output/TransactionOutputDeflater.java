package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressType;
import com.softwareverde.bitcoin.address.ParsedAddress;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.token.CashToken;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class TransactionOutputDeflater {
    protected ByteArray _toBytes(final TransactionOutput transactionOutput) {
        final byte[] valueBytes = ByteUtil.longToBytes(transactionOutput.getAmount());

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(valueBytes, Endian.LITTLE);

        final ByteArray scriptBytes = _getScriptBytes(transactionOutput);
        final int scriptByteCount = scriptBytes.getByteCount();
        final ByteArray scriptByteCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(scriptByteCount);

        byteArrayBuilder.appendBytes(scriptByteCountBytes);
        byteArrayBuilder.appendBytes(scriptBytes);

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    protected ByteArray _getScriptBytes(final TransactionOutput transactionOutput) {
        final LockingScript lockingScript = transactionOutput.getLockingScript();
        final ByteArray lockingScriptBytes = lockingScript.getBytes();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        final CashToken cashToken = transactionOutput.getCashToken();
        if (cashToken != null) {
            final ByteArray cashTokenBytes = cashToken.getBytes();

            byteArrayBuilder.appendBytes(cashTokenBytes, Endian.BIG);
            byteArrayBuilder.appendBytes(lockingScriptBytes, Endian.BIG);
        }
        else {
            byteArrayBuilder.appendBytes(lockingScriptBytes, Endian.BIG);
        }

        return byteArrayBuilder;
    }

    public Integer getByteCount(final TransactionOutput transactionOutput) {
        final int valueByteCount = 8;

        final Script lockingScript = transactionOutput.getLockingScript();
        final int scriptByteCount;
        if (transactionOutput.hasCashToken()) {
            final CashToken cashToken = transactionOutput.getCashToken();
            final int lockingScriptByteCount = lockingScript.getByteCount();
            final int cashTokenByteCount = cashToken.getByteCount();
            final int variableByteCount = (cashTokenByteCount + lockingScriptByteCount);

            int byteCount = 0;
            byteCount += CompactVariableLengthInteger.variableLengthIntegerToBytes(variableByteCount).getByteCount();
            byteCount += variableByteCount;
            scriptByteCount = byteCount;
        }
        else {
            int byteCount = 0;
            byteCount += CompactVariableLengthInteger.variableLengthIntegerToBytes(lockingScript.getByteCount()).getByteCount();
            byteCount += lockingScript.getByteCount();
            scriptByteCount = byteCount;
        }

        return (valueByteCount + scriptByteCount);
    }

    public ByteArray toBytes(final TransactionOutput transactionOutput) {
        return _toBytes(transactionOutput);
    }

    public ByteArray toLegacyScriptBytes(final TransactionOutput transactionOutput) {
        return _getScriptBytes(transactionOutput);
    }

    public Integer getLegacyScriptByteCount(final TransactionOutput transactionOutput) {
        final LockingScript lockingScript = transactionOutput.getLockingScript();
        final int lockingScriptByteCount = lockingScript.getByteCount();

        final CashToken cashToken = transactionOutput.getCashToken();
        if (cashToken != null) {
            return (lockingScriptByteCount + cashToken.getByteCount());
        }

        return lockingScriptByteCount;
    }

    public Json toJson(final TransactionOutput transactionOutput) {
        final Boolean isTokenAware = transactionOutput.hasCashToken();
        final CashToken cashToken = transactionOutput.getCashToken();

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final LockingScript lockingScript = transactionOutput.getLockingScript();
        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);

        final AddressType addressType = AddressType.fromScriptType(scriptType);
        final ParsedAddress parsedAddress = (address != null ? new ParsedAddress(addressType, isTokenAware, address) : null);

        final Json json = new Json();
        json.put("amount", transactionOutput.getAmount());
        json.put("index", transactionOutput.getIndex());
        json.put("lockingScript", transactionOutput.getLockingScript());
        json.put("cashToken", cashToken);
        json.put("type", scriptType);
        json.put("address", parsedAddress);

        // json.put("bytes", HexUtil.toHexString(_toBytes(transactionOutput)));

        return json;
    }
}

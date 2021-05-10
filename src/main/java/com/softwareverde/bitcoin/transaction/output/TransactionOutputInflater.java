package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.Endian;

public class TransactionOutputInflater {
    protected MutableTransactionOutput _fromByteArrayReader(final Integer index, final ByteArrayReader byteArrayReader) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();

        transactionOutput._amount = byteArrayReader.readLong(8, Endian.LITTLE);
        transactionOutput._index = index;

        final Integer scriptByteCount = byteArrayReader.readVariableLengthInteger().intValue();
        final ByteArray lockingScriptBytes = MutableByteArray.wrap(byteArrayReader.readBytes(scriptByteCount, Endian.BIG));

        // NOTE: Using an ImmutableLockingScript may be important for the performance of ScriptPatternMatcher::isProvablyUnspendable,
        //  which is used for UTXO acceptance into the UTXO Cache.
        transactionOutput._lockingScript = new ImmutableLockingScript(lockingScriptBytes);

        if (byteArrayReader.didOverflow()) { return null; }

        return transactionOutput;
    }

    public void _debugBytes(final ByteArrayReader byteArrayReader) {
        Logger.debug("Tx Output: Amount: " + MutableByteArray.wrap(byteArrayReader.readBytes(8)));

        final ByteArrayReader.CompactVariableLengthInteger variableLengthInteger = byteArrayReader.peakVariableLengthInteger();
        Logger.debug("Tx Output: Script Byte Count: " + HexUtil.toHexString(byteArrayReader.readBytes(variableLengthInteger.bytesConsumedCount)));
        Logger.debug("Tx Output: Script: " + HexUtil.toHexString(byteArrayReader.readBytes((int) variableLengthInteger.value)));
    }

    public MutableTransactionOutput fromBytes(final Integer index, final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(index, byteArrayReader);
    }

    public MutableTransactionOutput fromBytes(final Integer index, final ByteArray byteArray) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);
        return _fromByteArrayReader(index, byteArrayReader);
    }

    public MutableTransactionOutput fromBytes(final Integer index, final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(index, byteArrayReader);
    }
}

package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

import java.math.RoundingMode;

public class BlockHeaderDeflater {
    public static class BlockHeaderByteData {
        public final byte[] version = new byte[4];
        public final byte[] previousBlockHash = new byte[32];
        public final byte[] merkleRoot = new byte[32];
        public final byte[] timestamp = new byte[4];
        public final byte[] difficulty = new byte[4];
        public final byte[] nonce = new byte[4];
    }

    protected ByteArrayBuilder _serializeByteData(final BlockHeaderByteData byteData) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(byteData.version, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.previousBlockHash, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.merkleRoot, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.timestamp, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.difficulty, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.nonce, Endian.LITTLE);
        return byteArrayBuilder;
    }

    protected BlockHeaderByteData _createByteData(final BlockHeader blockHeader) {
        final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
        final MerkleRoot merkleRoot = blockHeader.getMerkleRoot();

        final BlockHeaderByteData byteData = new BlockHeaderByteData();
        ByteUtil.setBytes(byteData.version, ByteUtil.integerToBytes(blockHeader.getVersion()));
        ByteUtil.setBytes(byteData.previousBlockHash, previousBlockHash.getBytes());
        ByteUtil.setBytes(byteData.merkleRoot, merkleRoot.getBytes());

        final byte[] timestampBytes = ByteUtil.longToBytes(blockHeader.getTimestamp());
        for (int i = 0; i < byteData.timestamp.length; ++i) {
            byteData.timestamp[(byteData.timestamp.length - i) - 1] = timestampBytes[(timestampBytes.length - i) - 1];
        }

        final Difficulty difficulty = blockHeader.getDifficulty();
        ByteUtil.setBytes(MutableByteArray.wrap(byteData.difficulty), difficulty.encode());

        final byte[] nonceBytes = ByteUtil.longToBytes(blockHeader.getNonce());
        for (int i = 0; i < byteData.nonce.length; ++i) {
            byteData.nonce[(byteData.nonce.length - i) - 1] = nonceBytes[(nonceBytes.length - i) - 1];
        }

        return byteData;
    }

    public ByteArray toBytes(final BlockHeader blockHeader) {
        final BlockHeaderByteData blockHeaderByteData = _createByteData(blockHeader);
        return _serializeByteData(blockHeaderByteData);
    }

    public ByteArrayBuilder toByteArrayBuilder(final BlockHeader blockHeader) {
        final BlockHeaderByteData blockHeaderByteData = _createByteData(blockHeader);
        return _serializeByteData(blockHeaderByteData);
    }

    public Json toJson(final BlockHeader blockHeader) {
        final Json json = new Json();

        json.put("hash", blockHeader.getHash());
        json.put("previousBlockHash", blockHeader.getPreviousBlockHash());
        json.put("merkleRoot", blockHeader.getMerkleRoot());
        json.put("version", blockHeader.getVersion());

        { // Timestamp Json...
            final Long timestamp = blockHeader.getTimestamp();

            final Json timestampJson = new Json();
            timestampJson.put("date", DateUtil.Utc.timestampToDatetimeString(timestamp * 1000L));
            timestampJson.put("value", timestamp);
            json.put("timestamp", timestampJson);
        }

        { // Difficulty Json...
            final Difficulty difficulty = blockHeader.getDifficulty();

            final Json difficultyJson = new Json();
            difficultyJson.put("ratio", difficulty.getDifficultyRatio().setScale(2, RoundingMode.HALF_UP));
            difficultyJson.put("value", difficulty.encode());
            difficultyJson.put("mask", difficulty.getBytes());
            json.put("difficulty", difficultyJson);
        }

        json.put("nonce", blockHeader.getNonce());

        return json;
    }
}

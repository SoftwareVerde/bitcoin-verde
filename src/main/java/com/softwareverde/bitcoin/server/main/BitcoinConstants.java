package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

public class BitcoinConstants {
    public static final Integer DATABASE_VERSION = 11;

    public static class Default {
        public final String genesisBlockHash;
        public final Long genesisBlockTimestamp;
        public final Integer defaultNetworkPort;
        public final String netMagicNumber;
        public final Integer defaultRpcPort;
        public final AsertReferenceBlock asertReferenceBlock;
        public final Integer defaultBlockMaxByteCount;

        protected Default(final String genesisBlockHash, final Long genesisBlockTimestamp, final Integer defaultNetworkPort, final String netMagicNumber, final AsertReferenceBlock asertReferenceBlock, final Integer blockMaxByteCount) {
            this.genesisBlockHash = genesisBlockHash;
            this.genesisBlockTimestamp = genesisBlockTimestamp;
            this.defaultNetworkPort = defaultNetworkPort;
            this.netMagicNumber = netMagicNumber;
            this.defaultRpcPort = 8334;
            this.asertReferenceBlock = asertReferenceBlock;
            this.defaultBlockMaxByteCount = blockMaxByteCount;
        }
    }

    public enum Network {
        MAIN, TEST, TEST4, SCALE
    }

    private static final String LOCKED_ERROR_MESSAGE = "Attempting to set SystemProperty after initialization.";
    private static Boolean LOCKED = false;

    protected static String GENESIS_BLOCK_HASH;
    protected static Long GENESIS_BLOCK_TIMESTAMP;
    protected static String NET_MAGIC_NUMBER;
    protected static Integer DEFAULT_NETWORK_PORT;
    protected static Integer DEFAULT_TEST_NETWORK_PORT;
    protected static Integer DEFAULT_RPC_PORT;
    protected static Integer DEFAULT_TEST_RPC_PORT;

    @Deprecated
    protected static Integer TRANSACTION_MIN_BYTE_COUNT_HF20181115 = 100;
    protected static Integer TRANSACTION_MIN_BYTE_COUNT = 65;
    protected static Integer TRANSACTION_MAX_BYTE_COUNT = (int) (2L * ByteUtil.Unit.Si.MEGABYTES);
    protected static Integer BLOCK_MAX_BYTE_COUNT = (int) (32L * ByteUtil.Unit.Si.MEGABYTES);

    protected static Long BLOCK_VERSION;
    protected static Long TRANSACTION_VERSION;
    protected static Integer PROTOCOL_VERSION;
    protected static String USER_AGENT;
    protected static String COINBASE_MESSAGE;

    protected static List<UtxoCommitmentMetadata> UTXO_COMMITMENTS;

    protected static AsertReferenceBlock ASERT_REFERENCE_BLOCK = new AsertReferenceBlock(
        661647L,
        1605447844L,
        Difficulty.decode(ByteArray.fromHexString("1804DAFE"))
    );

    protected static AsertReferenceBlock TEST_NET_ASERT_REFERENCE_BLOCK = new AsertReferenceBlock(
        1421481L,
        1605445400L,
        Difficulty.decode(ByteArray.wrap(HexUtil.hexStringToByteArray("1D00FFFF")))
    );

    protected static AsertReferenceBlock TEST_NET4_ASERT_REFERENCE_BLOCK = new AsertReferenceBlock(
        16844L,
        1605451779L,
        Difficulty.decode(ByteArray.wrap(HexUtil.hexStringToByteArray("1D00FFFF")))
    );

    public static final Default MainNet = new Default(
        "000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F",
        1231006505L, // In seconds.
        8333,
        "E8F3E1E3",
        ASERT_REFERENCE_BLOCK,
        (int) (32L * ByteUtil.Unit.Si.MEGABYTES)
    );

    public static final Default TestNet = new Default(
        "000000000933EA01AD0EE984209779BAAEC3CED90FA3F408719526F8D77F4943",
        1296688602L, // In seconds.
        18333,
        "F4F3E5F4",
        TEST_NET_ASERT_REFERENCE_BLOCK,
        (int) (32L * ByteUtil.Unit.Si.MEGABYTES)
    );

    public static final Default TestNet4 = new Default(
        "000000001DD410C49A788668CE26751718CC797474D3152A5FC073DD44FD9F7B",
        1597811185L, // In seconds.
        28333,
        "AFDAB7E2",
        TEST_NET4_ASERT_REFERENCE_BLOCK,
        (int) (32L * ByteUtil.Unit.Si.MEGABYTES)
    );

    public static final Default ChipNet = new Default(
        "000000001DD410C49A788668CE26751718CC797474D3152A5FC073DD44FD9F7B",
        1597811185L, // In seconds.
        28333,
        "AFDAB7E2",
        TEST_NET4_ASERT_REFERENCE_BLOCK,
        (int) (32L * ByteUtil.Unit.Si.MEGABYTES)
    );

    static {
        final Long defaultBlockVersion = 0x04L;
        final Long defaultTransactionVersion = 0x02L;
        final Integer defaultProtocolVersion = 70015;
        final String defaultUserAgent = "/Bitcoin Verde:2.3.0/";
        final String coinbaseMessage = "/pool.bitcoinverde.org/VERDE/";

        // SELECT blocks.hash, blocks.block_height, utxo_commitments.public_key, SUM(utxo_commitment_files.byte_count) AS byte_count FROM blocks INNER JOIN utxo_commitments ON utxo_commitments.block_id = blocks.id INNER JOIN utxo_commitment_buckets ON utxo_commitment_buckets.utxo_commitment_id = utxo_commitments.id INNER JOIN utxo_commitment_files ON utxo_commitment_files.utxo_commitment_bucket_id = utxo_commitment_buckets.id GROUP BY utxo_commitments.id ORDER BY blocks.block_height ASC;
        final String utxoCommitmentsString =
            // Sha256Hash blockHash, Long blockHeight, PublicKey multisetPublicKey, Long byteCount
            "00000000000000000621B8F668B02530BCF77446E85E56CB0257A51B4EC980BA,750000,03DFCEAE98BB44D0E3D7EE21FA7BED28BC338D014053E2B4BDF1B8F66C29DA66F3,5174255942;" +
            "000000000000000000480527D21EB07089D8390CAEF5008BA2971FD554777FAE,760000,0302B84B38922825D8FE159CF42A9D5141D26240D2C73A7238F063172632C1F50D,5312373328";

        GENESIS_BLOCK_HASH = System.getProperty("GENESIS_BLOCK_HASH", MainNet.genesisBlockHash);
        GENESIS_BLOCK_TIMESTAMP = Util.parseLong(System.getProperty("GENESIS_BLOCK_TIMESTAMP", String.valueOf(MainNet.genesisBlockTimestamp)));
        DEFAULT_NETWORK_PORT = Util.parseInt(System.getProperty("NETWORK_PORT", String.valueOf(MainNet.defaultNetworkPort)));
        DEFAULT_TEST_NETWORK_PORT = Util.parseInt(System.getProperty("TEST_NETWORK_PORT", String.valueOf(TestNet.defaultNetworkPort)));
        DEFAULT_TEST_RPC_PORT = Util.parseInt(System.getProperty("TEST_RPC_PORT", String.valueOf(TestNet.defaultRpcPort)));
        NET_MAGIC_NUMBER = System.getProperty("NET_MAGIC_NUMBER", MainNet.netMagicNumber);
        DEFAULT_RPC_PORT = Util.parseInt(System.getProperty("RPC_PORT", String.valueOf(MainNet.defaultRpcPort)));
        BLOCK_VERSION = Util.parseLong(System.getProperty("BLOCK_VERSION", String.valueOf(defaultBlockVersion)), defaultBlockVersion);
        TRANSACTION_VERSION = Util.parseLong(System.getProperty("TRANSACTION_VERSION", String.valueOf(defaultTransactionVersion)), defaultTransactionVersion);
        PROTOCOL_VERSION = Util.parseInt(System.getProperty("PROTOCOL_VERSION", String.valueOf(defaultProtocolVersion)), defaultProtocolVersion);
        USER_AGENT = System.getProperty("USER_AGENT", defaultUserAgent);
        COINBASE_MESSAGE = System.getProperty("COINBASE_MESSAGE", coinbaseMessage);

        { // Parse UTXO commitments...
            final ImmutableListBuilder<UtxoCommitmentMetadata> utxoCommitments = new ImmutableListBuilder<>();
            final String loadedProperty = System.getProperty("UTXO_COMMITMENTS", utxoCommitmentsString);
            for (final String commitmentTupleString : loadedProperty.split(";")) {
                final String[] hashesString = commitmentTupleString.split(",");
                if (hashesString.length != 4) { continue; }

                final Sha256Hash blockHash = Sha256Hash.fromHexString(hashesString[0]);
                final Long blockHeight = Util.parseLong(hashesString[1]);
                final PublicKey multisetPublicKey = PublicKey.fromHexString(hashesString[2]);
                final Long byteCount = Util.parseLong(hashesString[3]);

                utxoCommitments.add(new UtxoCommitmentMetadata(blockHash, blockHeight, multisetPublicKey, byteCount));
            }
            UTXO_COMMITMENTS = utxoCommitments.build();
        }
    }

    public static void configureForNetwork(final NetworkType networkType) {
        switch (networkType) {
            case TEST_NET: {
                BitcoinConstants.setGenesisBlockHash(BitcoinConstants.TestNet.genesisBlockHash);
                BitcoinConstants.setGenesisBlockTimestamp(BitcoinConstants.TestNet.genesisBlockTimestamp);
                BitcoinConstants.setNetMagicNumber(BitcoinConstants.TestNet.netMagicNumber);
                BitcoinConstants.setDefaultNetworkPort(BitcoinConstants.TestNet.defaultNetworkPort);
                BitcoinConstants.setDefaultRpcPort(BitcoinConstants.TestNet.defaultRpcPort);
                BitcoinConstants.setAsertReferenceBlock(BitcoinConstants.TestNet.asertReferenceBlock);
            } break;
            case TEST_NET4: {
                BitcoinConstants.setGenesisBlockHash(BitcoinConstants.TestNet4.genesisBlockHash);
                BitcoinConstants.setGenesisBlockTimestamp(BitcoinConstants.TestNet4.genesisBlockTimestamp);
                BitcoinConstants.setNetMagicNumber(BitcoinConstants.TestNet4.netMagicNumber);
                BitcoinConstants.setDefaultNetworkPort(BitcoinConstants.TestNet4.defaultNetworkPort);
                BitcoinConstants.setDefaultRpcPort(BitcoinConstants.TestNet4.defaultRpcPort);
                BitcoinConstants.setAsertReferenceBlock(BitcoinConstants.TestNet4.asertReferenceBlock);
            } break;
            case CHIP_NET: {
                BitcoinConstants.setGenesisBlockHash(BitcoinConstants.ChipNet.genesisBlockHash);
                BitcoinConstants.setGenesisBlockTimestamp(BitcoinConstants.ChipNet.genesisBlockTimestamp);
                BitcoinConstants.setNetMagicNumber(BitcoinConstants.ChipNet.netMagicNumber);
                BitcoinConstants.setDefaultNetworkPort(BitcoinConstants.ChipNet.defaultNetworkPort);
                BitcoinConstants.setDefaultRpcPort(BitcoinConstants.ChipNet.defaultRpcPort);
                BitcoinConstants.setAsertReferenceBlock(BitcoinConstants.ChipNet.asertReferenceBlock);
            } break;
            default: {
                BitcoinConstants.setGenesisBlockHash(BitcoinConstants.MainNet.genesisBlockHash);
                BitcoinConstants.setGenesisBlockTimestamp(BitcoinConstants.MainNet.genesisBlockTimestamp);
                BitcoinConstants.setNetMagicNumber(BitcoinConstants.MainNet.netMagicNumber);
                BitcoinConstants.setDefaultNetworkPort(BitcoinConstants.MainNet.defaultNetworkPort);
                BitcoinConstants.setDefaultRpcPort(BitcoinConstants.MainNet.defaultRpcPort);
                BitcoinConstants.setAsertReferenceBlock(BitcoinConstants.MainNet.asertReferenceBlock);
            }
        }
    }

    public static Boolean isLocked() {
        return LOCKED;
    }

    public static String getGenesisBlockHash() {
        LOCKED = true;
        return GENESIS_BLOCK_HASH;
    }

    public static void setGenesisBlockHash(final String genesisBlockHash) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        GENESIS_BLOCK_HASH = genesisBlockHash;
    }

    public static Long getGenesisBlockTimestamp() {
        LOCKED = true;
        return GENESIS_BLOCK_TIMESTAMP;
    }

    public static void setGenesisBlockTimestamp(final Long genesisBlockTimestamp) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        GENESIS_BLOCK_TIMESTAMP = genesisBlockTimestamp;
    }

    public static Integer getDefaultNetworkPort() {
        LOCKED = true;
        return DEFAULT_NETWORK_PORT;
    }

    public static void setDefaultNetworkPort(final Integer defaultNetworkPort) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        DEFAULT_NETWORK_PORT = defaultNetworkPort;
    }

    public static Integer getDefaultTestNetworkPort() {
        LOCKED = true;
        return DEFAULT_TEST_NETWORK_PORT;
    }

    public static void setDefaultTestNetworkPort(final Integer defaultNetworkPort) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        DEFAULT_TEST_NETWORK_PORT = defaultNetworkPort;
    }

    public static Integer getDefaultTestRpcPort() {
        LOCKED = true;
        return DEFAULT_TEST_RPC_PORT;
    }

    public static void setDefaultTestRpcPort(final Integer defaultTestRpcPort) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        DEFAULT_TEST_RPC_PORT = defaultTestRpcPort;
    }

    public static String getNetMagicNumber() {
        LOCKED = true;
        return NET_MAGIC_NUMBER;
    }

    public static void setNetMagicNumber(final String netMagicNumber) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        NET_MAGIC_NUMBER = netMagicNumber;
    }

    public static Integer getDefaultRpcPort() {
        LOCKED = true;
        return DEFAULT_RPC_PORT;
    }

    public static void setDefaultRpcPort(final Integer defaultRpcPort) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        DEFAULT_RPC_PORT = defaultRpcPort;
    }

    public static AsertReferenceBlock getAsertReferenceBlock() {
        LOCKED = true;
        return ASERT_REFERENCE_BLOCK;
    }

    public static void setAsertReferenceBlock(final AsertReferenceBlock asertReferenceBlock) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        ASERT_REFERENCE_BLOCK = asertReferenceBlock;
    }

    public static Integer getTransactionMinByteCount() {
        LOCKED = true;
        return TRANSACTION_MIN_BYTE_COUNT;
    }

    public static void setTransactionMinByteCount(final Integer byteCount) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        TRANSACTION_MIN_BYTE_COUNT = byteCount;
    }

    public static Integer getTransactionMaxByteCount() {
        LOCKED = true;
        return TRANSACTION_MAX_BYTE_COUNT;
    }

    public static void setTransactionMaxByteCount(final Integer byteCount) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        TRANSACTION_MAX_BYTE_COUNT = byteCount;
    }

    public static Integer getBlockMaxByteCount() {
        LOCKED = true;
        return BLOCK_MAX_BYTE_COUNT;
    }

    public static void setBlockMaxByteCount(final Integer byteCount) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        BLOCK_MAX_BYTE_COUNT = byteCount;
    }

    public static Integer getMaxTransactionCountPerBlock() {
        final int blockMaxByteCount = BitcoinConstants.getBlockMaxByteCount();
        final int transactionMinByteCount = BitcoinConstants.getTransactionMinByteCount();
        return (blockMaxByteCount / transactionMinByteCount);
    }

    public static Long getBlockVersion() {
        LOCKED = true;
        return BLOCK_VERSION;
    }

    public static void setBlockVersion(final Long blockVersion) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        BLOCK_VERSION = blockVersion;
    }

    public static Long getTransactionVersion() {
        LOCKED = true;
        return TRANSACTION_VERSION;
    }

    public static void setTransactionVersion(final Long blockVersion) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        TRANSACTION_VERSION = blockVersion;
    }

    public static Integer getProtocolVersion() {
        LOCKED = true;
        return PROTOCOL_VERSION;
    }

    public static void setProtocolVersion(final Integer protocolVersion) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        PROTOCOL_VERSION = protocolVersion;
    }

    public static String getUserAgent() {
        LOCKED = true;
        return USER_AGENT;
    }

    public static void setUserAgent(final String userAgent) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        USER_AGENT = userAgent;
    }

    public static String getCoinbaseMessage() {
        LOCKED = true;
        return COINBASE_MESSAGE;
    }

    public static void setCoinbaseMessage(final String coinbaseMessage) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        COINBASE_MESSAGE = coinbaseMessage;
    }

    public static List<UtxoCommitmentMetadata> getUtxoCommitments() {
        return UTXO_COMMITMENTS;
    }

    public static void setUtxoCommitments(final List<UtxoCommitmentMetadata> utxoCommitmentMetadata) {
        if (LOCKED) {
            throw new RuntimeException(LOCKED_ERROR_MESSAGE);
        }

        UTXO_COMMITMENTS = utxoCommitmentMetadata;
    }

    protected BitcoinConstants() { }
}

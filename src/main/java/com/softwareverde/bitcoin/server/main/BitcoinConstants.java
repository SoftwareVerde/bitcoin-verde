package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

public class BitcoinConstants {
    public static final Integer DATABASE_VERSION = 7;

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

    private static final String LOCKED_ERROR_MESSAGE = "Attempting to set SystemProperty after initialization.";
    private static Boolean LOCKED = false;

    protected static String GENESIS_BLOCK_HASH;
    protected static Long GENESIS_BLOCK_TIMESTAMP;
    protected static String NET_MAGIC_NUMBER;
    protected static Integer DEFAULT_NETWORK_PORT;
    protected static Integer DEFAULT_TEST_NETWORK_PORT;
    protected static Integer DEFAULT_RPC_PORT;
    protected static Integer DEFAULT_TEST_RPC_PORT;

    protected static Integer TRANSACTION_MIN_BYTE_COUNT = 100;
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

    protected static final String BITCOIN_SIGNATURE_MESSAGE_MAGIC;

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

    static {
        final Long defaultBlockVersion = 0x04L;
        final Long defaultTransactionVersion = 0x02L;
        final Integer defaultProtocolVersion = 70015;
        final String defaultUserAgent = "/Bitcoin Verde:2.0.1/";
        final String coinbaseMessage = "/pool.bitcoinverde.org/VERDE/";
        final String utxoCommitmentsString =
            "00000000000000000156405044E3F8654A4713F36E1E62CDBE135F5E7337F3B9,513CD651DA36F4961FED8A2E195641D13C8EEFD7C96453A7B2F9A6122D7FDB7E,2996093853;" +
            "00000000000000000040B37F904A9CBBA25A6D37AA313D4AE8C4C46589CF4C6E,23C3A136BB523CCDDB032A56EF62CCA495B5B966F64EA2BF35449164D49FCC9A,3908702538";

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
                if (hashesString.length != 3) { continue; }

                final Sha256Hash blockHash = Sha256Hash.fromHexString(hashesString[0]);
                final Sha256Hash multisetHash = Sha256Hash.fromHexString(hashesString[1]);
                final Long byteCount = Util.parseLong(hashesString[2]);

                utxoCommitments.add(new UtxoCommitmentMetadata(blockHash, multisetHash, byteCount));
            }
            UTXO_COMMITMENTS = utxoCommitments.build();
        }

        BITCOIN_SIGNATURE_MESSAGE_MAGIC = "Bitcoin Signed Message:\n";
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

    public static String getBitcoinSignatureMessageMagic() {
        return BITCOIN_SIGNATURE_MESSAGE_MAGIC;
    }

    public static List<UtxoCommitmentMetadata> getUtxoCommitments() {
        return UTXO_COMMITMENTS;
    }

    protected BitcoinConstants() { }
}

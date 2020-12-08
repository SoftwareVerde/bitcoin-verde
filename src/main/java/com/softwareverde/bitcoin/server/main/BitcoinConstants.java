package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.Util;

public class BitcoinConstants {
    protected static final Integer DATABASE_VERSION = 3;

    public static class Default {
        public final String genesisBlockHash;
        public final Long genesisBlockTimestamp;
        public final Integer defaultNetworkPort;
        public final String netMagicNumber;
        public final Integer defaultRpcPort;

        protected Default(final String genesisBlockHash, final Long genesisBlockTimestamp, final Integer defaultNetworkPort, final String netMagicNumber) {
            this.genesisBlockHash = genesisBlockHash;
            this.genesisBlockTimestamp = genesisBlockTimestamp;
            this.defaultNetworkPort = defaultNetworkPort;
            this.netMagicNumber = netMagicNumber;
            this.defaultRpcPort = 8334;
        }
    }

    public static final Default MainNet = new Default(
        "000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F",
        1231006505L, // In seconds.
        8333,
        "E8F3E1E3"
    );

    public static final Default TestNet = new Default(
        "000000000933EA01AD0EE984209779BAAEC3CED90FA3F408719526F8D77F4943",
        1296688602L, // In seconds.
        18333,
        "F4F3E5F4"
    );

    private static final String LOCKED_ERROR_MESSAGE = "Attempting to set SystemProperty after initialization.";
    private static Boolean LOCKED = false;

    protected static String GENESIS_BLOCK_HASH;
    protected static Long GENESIS_BLOCK_TIMESTAMP;
    protected static String NET_MAGIC_NUMBER;
    protected static Integer DEFAULT_NETWORK_PORT;
    protected static Integer DEFAULT_TEST_NETWORK_PORT;
    protected static Integer DEFAULT_RPC_PORT;
    protected static Integer DEFAULT_TEST_RPC_PORT;

    protected static Long BLOCK_VERSION;
    protected static Long TRANSACTION_VERSION;
    protected static Integer PROTOCOL_VERSION;
    protected static String USER_AGENT;
    protected static String COINBASE_MESSAGE;

    protected static Boolean REQUIRE_BITCOIN_CASH_FORK_ID;
    protected static Boolean REQUIRE_MINIMAL_ENCODED_VALUES;

    protected static final String BITCOIN_SIGNATURE_MESSAGE_MAGIC;

    protected static final AsertReferenceBlock ASERT_REFERENCE_BLOCK;

    static {
        final Long defaultBlockVersion = 0x04L;
        final Long defaultTransactionVersion = 0x02L;
        final Integer defaultProtocolVersion = 70015;
        final String defaultUserAgent = "/Bitcoin Verde:2.0.0/";
        final String coinbaseMessage = "/pool.bitcoinverde.org/";

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

        ASERT_REFERENCE_BLOCK = new AsertReferenceBlock(
            661647L,
            1605447844L,
            Difficulty.decode(ByteArray.fromHexString("1804DAFE"))
        );

        REQUIRE_BITCOIN_CASH_FORK_ID = true;
        REQUIRE_MINIMAL_ENCODED_VALUES = true;

        BITCOIN_SIGNATURE_MESSAGE_MAGIC = "Bitcoin Signed Message:\n";
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

    // Aka the "SIGHASH_FORKID" / "SCRIPT_ENABLE_SIGHASH_FORKID" flags.
    public static Boolean requireBitcoinCashForkId() {
        return REQUIRE_BITCOIN_CASH_FORK_ID;
    }

    // Aka the "STRICTENC" flag.
    public static Boolean valuesMustBeMinimallyEncoded() {
        return REQUIRE_MINIMAL_ENCODED_VALUES;
    }

    public static String getBitcoinSignatureMessageMagic() {
        return BITCOIN_SIGNATURE_MESSAGE_MAGIC;
    }

    public static AsertReferenceBlock getAsertReferenceBlock() {
        return ASERT_REFERENCE_BLOCK;
    }

    protected BitcoinConstants() { }
}

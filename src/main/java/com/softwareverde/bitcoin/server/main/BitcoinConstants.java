package com.softwareverde.bitcoin.server.main;

import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class BitcoinConstants {
    public static class Default {
        public final String genesisBlockHash;
        public final Long genesisBlockTimestamp;
        public final String netMagicNumber;

        protected Default(final String genesisBlockHash, final Long genesisBlockTimestamp, final String netMagicNumber) {
            this.genesisBlockHash = genesisBlockHash;
            this.genesisBlockTimestamp = genesisBlockTimestamp;
            this.netMagicNumber = netMagicNumber;
        }
    }

    public static final Default MainNet = new Default(
        "000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F",
        1231006505L, // In seconds.
        "E8F3E1E3"
    );

    public static final Default TestNet = new Default(
        "000000000933EA01AD0EE984209779BAAEC3CED90FA3F408719526F8D77F4943",
        1296688602L, // In seconds.
        "F4F3E5F4"
    );

    protected static final Integer DATABASE_VERSION = 2;

    private static final String LOCKED_ERROR_MESSAGE = "Attempting to set SystemProperty after initialization.";
    private static Boolean LOCKED = false;

    protected static String GENESIS_BLOCK_HASH;
    protected static Long GENESIS_BLOCK_TIMESTAMP;
    protected static String NET_MAGIC_NUMBER;

    protected static Long BLOCK_VERSION;
    protected static Long TRANSACTION_VERSION;
    protected static Integer PROTOCOL_VERSION;
    protected static String USER_AGENT;
    protected static String COINBASE_MESSAGE;

    protected static Boolean FAIL_ON_BAD_SIGNATURE;
    protected static Boolean REQUIRE_BITCOIN_CASH_FORK_ID;
    protected static Boolean REQUIRE_MINIMAL_ENCODED_VALUES;

    protected static final String BITCOIN_SIGNATURE_MESSAGE_MAGIC;

    static {
        final Long defaultBlockVersion = 0x04L;
        final Long defaultTransactionVersion = 0x02L;
        final Integer defaultProtocolVersion = 70015;
        final String defaultUserAgent = "/Bitcoin Verde:1.2.2/";
        final String coinbaseMessage = "/pool.bitcoinverde.org/";

        GENESIS_BLOCK_HASH = System.getProperty("GENESIS_BLOCK_HASH", MainNet.genesisBlockHash);
        GENESIS_BLOCK_TIMESTAMP = Util.parseLong(System.getProperty("GENESIS_BLOCK_TIMESTAMP", MainNet.genesisBlockTimestamp.toString()));
        NET_MAGIC_NUMBER = System.getProperty("NET_MAGIC_NUMBER", MainNet.netMagicNumber);
        BLOCK_VERSION = Util.parseLong(System.getProperty("BLOCK_VERSION", defaultBlockVersion.toString()), defaultBlockVersion);
        TRANSACTION_VERSION = Util.parseLong(System.getProperty("TRANSACTION_VERSION", defaultTransactionVersion.toString()), defaultTransactionVersion);
        PROTOCOL_VERSION = Util.parseInt(System.getProperty("PROTOCOL_VERSION", defaultProtocolVersion.toString()), defaultProtocolVersion);
        USER_AGENT = System.getProperty("USER_AGENT", defaultUserAgent);
        COINBASE_MESSAGE = System.getProperty("COINBASE_MESSAGE", coinbaseMessage);

        FAIL_ON_BAD_SIGNATURE = true;
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
            Logger.error(LOCKED_ERROR_MESSAGE);
            return;
        }

        GENESIS_BLOCK_HASH = genesisBlockHash;
    }

    public static Long getGenesisBlockTimestamp() {
        LOCKED = true;
        return GENESIS_BLOCK_TIMESTAMP;
    }

    public static void setGenesisBlockTimestamp(final Long genesisBlockTimestamp) {
        if (LOCKED) {
            Logger.error(LOCKED_ERROR_MESSAGE);
            return;
        }

        GENESIS_BLOCK_TIMESTAMP = genesisBlockTimestamp;
    }

    public static String getNetMagicNumber() {
        LOCKED = true;
        return NET_MAGIC_NUMBER;
    }

    public static void setNetMagicNumber(final String netMagicNumber) {
        if (LOCKED) {
            Logger.error(LOCKED_ERROR_MESSAGE);
            return;
        }

        NET_MAGIC_NUMBER = netMagicNumber;
    }

    public static Long getBlockVersion() {
        LOCKED = true;
        return BLOCK_VERSION;
    }

    public static void setBlockVersion(final Long blockVersion) {
        if (LOCKED) {
            Logger.error(LOCKED_ERROR_MESSAGE);
            return;
        }

        BLOCK_VERSION = blockVersion;
    }

    public static Long getTransactionVersion() {
        LOCKED = true;
        return TRANSACTION_VERSION;
    }

    public static void setTransactionVersion(final Long blockVersion) {
        if (LOCKED) {
            Logger.error(LOCKED_ERROR_MESSAGE);
            return;
        }

        TRANSACTION_VERSION = blockVersion;
    }

    public static Integer getProtocolVersion() {
        LOCKED = true;
        return PROTOCOL_VERSION;
    }

    public static void setProtocolVersion(final Integer protocolVersion) {
        if (LOCKED) {
            Logger.error(LOCKED_ERROR_MESSAGE);
            return;
        }

        PROTOCOL_VERSION = protocolVersion;
    }

    public static String getUserAgent() {
        LOCKED = true;
        return USER_AGENT;
    }

    public static void setUserAgent(final String userAgent) {
        if (LOCKED) {
            Logger.error(LOCKED_ERROR_MESSAGE);
            return;
        }

        USER_AGENT = userAgent;
    }

    public static String getCoinbaseMessage() {
        LOCKED = true;
        return COINBASE_MESSAGE;
    }

    public static void setCoinbaseMessage(final String coinbaseMessage) {
        if (LOCKED) {
            Logger.error(LOCKED_ERROR_MESSAGE);
            return;
        }

        COINBASE_MESSAGE = coinbaseMessage;
    }

    // Aka the "NULLFAIL" flag.
    public static Boolean immediatelyFailOnNonEmptyInvalidSignatures() {
        return FAIL_ON_BAD_SIGNATURE;
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

    protected BitcoinConstants() { }
}

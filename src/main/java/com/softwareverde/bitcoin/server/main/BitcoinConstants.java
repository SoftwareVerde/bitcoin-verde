package com.softwareverde.bitcoin.server.main;

import com.softwareverde.util.Util;

public class BitcoinConstants {
    protected static final Integer DATABASE_VERSION = 1;

    private static final String LOCKED_ERROR_MESSAGE = "Attempting to set SystemProperty after initialization.";
    private static Boolean LOCKED = false;

    protected static String GENESIS_BLOCK_HASH;
    protected static String NET_MAGIC_NUMBER;
    protected static Long BLOCK_VERSION;
    protected static Long TRANSACTION_VERSION;
    protected static Integer PROTOCOL_VERSION;
    protected static String USER_AGENT;
    protected static String COINBASE_MESSAGE;

    static {
        final String defaultBlockHash = "000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F";
        final String defaultNetMagicNumber = "E8F3E1E3";
        final Long defaultBlockVersion = 0x04L;
        final Long defaultTransactionVersion = 0x02L;
        final Integer defaultProtocolVersion = 70015;
        final String defaultUserAgent = "/Bitcoin Verde:1.0.1/";
        final String coinbaseMessage = "/pool.bitcoinverde.org/";

        GENESIS_BLOCK_HASH = System.getProperty("GENESIS_BLOCK_HASH", defaultBlockHash);
        NET_MAGIC_NUMBER = System.getProperty("NET_MAGIC_NUMBER", defaultNetMagicNumber);
        BLOCK_VERSION = Util.parseLong(System.getProperty("BLOCK_VERSION", defaultBlockVersion.toString()), defaultBlockVersion);
        TRANSACTION_VERSION = Util.parseLong(System.getProperty("TRANSACTION_VERSION", defaultTransactionVersion.toString()), defaultTransactionVersion);
        PROTOCOL_VERSION = Util.parseInt(System.getProperty("PROTOCOL_VERSION", defaultProtocolVersion.toString()), defaultProtocolVersion);
        USER_AGENT = System.getProperty("USER_AGENT", defaultUserAgent);
        COINBASE_MESSAGE = System.getProperty("COINBASE_MESSAGE", coinbaseMessage);
    }

    public static String getGenesisBlockHash() {
        LOCKED = true;
        return GENESIS_BLOCK_HASH;
    }

    protected static void setGenesisBlockHash(final String genesisBlockHash) {
        if (LOCKED) {
            System.err.println(LOCKED_ERROR_MESSAGE);
            return;
        }

        GENESIS_BLOCK_HASH = genesisBlockHash;
    }

    public static String getNetMagicNumber() {
        LOCKED = true;
        return NET_MAGIC_NUMBER;
    }

    public static void setNetMagicNumber(final String netMagicNumber) {
        if (LOCKED) {
            System.err.println(LOCKED_ERROR_MESSAGE);
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
            System.err.println(LOCKED_ERROR_MESSAGE);
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
            System.err.println(LOCKED_ERROR_MESSAGE);
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
            System.err.println(LOCKED_ERROR_MESSAGE);
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
            System.err.println(LOCKED_ERROR_MESSAGE);
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
            System.err.println(LOCKED_ERROR_MESSAGE);
            return;
        }

        COINBASE_MESSAGE = coinbaseMessage;
    }

    protected BitcoinConstants() { }
}

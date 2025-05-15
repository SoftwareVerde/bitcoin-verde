package com.softwareverde.util;

public class BitcoinSystemUtil extends SystemUtil {
    protected BitcoinSystemUtil() { }

    public static Boolean isAppleSiliconArchitecture() {
        if (! SystemUtil.isMacOperatingSystem()) { return false; }
        final String architecture = System.getProperty("os.arch").toLowerCase();
        return architecture.contains("aarch64");
    }
}

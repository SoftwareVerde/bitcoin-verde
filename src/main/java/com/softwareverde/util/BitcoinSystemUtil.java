package com.softwareverde.util;

public class BitcoinSystemUtil extends SystemUtil {
    protected BitcoinSystemUtil() { }

    public static Boolean isAppleSiliconArchitecture() {
        return (SystemUtil.isMacOperatingSystem() && Util.areEqual(System.getProperty("os.arch"), "aarch64"));
    }
}

package com.softwareverde.util;

import java.lang.management.ManagementFactory;

public class SystemUtil {
    protected SystemUtil() { }

    public static Long getProcessId() {
        // Usually '<pid>@<hostname>'...
        final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        return Util.parseLong(jvmName); // Relies on Util::parseLong being very tolerant of strings containing non-numeric characters...
    }

    public static Boolean isWindowsOperatingSystem() {
        final String operatingSystemName = System.getProperty("os.name").toLowerCase();
        return operatingSystemName.startsWith("windows");
    }

    public static Boolean isMacOperatingSystem() {
        final String operatingSystemName = System.getProperty("os.name").toLowerCase();
        return operatingSystemName.contains("mac");
    }

    public static Boolean isLinuxOperatingSystem() {
        final String operatingSystemName = System.getProperty("os.name").toLowerCase();
        return operatingSystemName.contains("linux");
    }
}

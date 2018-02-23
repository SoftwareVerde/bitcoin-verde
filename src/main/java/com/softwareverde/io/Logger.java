package com.softwareverde.io;

public class Logger {
    public static void log(final Object object) {
        if (object instanceof Exception) {
            ((Exception) object).printStackTrace();
        }
        else {
            System.out.println(object);
        }
    }
}

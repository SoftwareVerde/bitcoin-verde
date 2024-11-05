package com.softwareverde.bitcoin.util;

public class MathUtil {
    public static Long add(final long left, final long right) {
        try {
            return Math.addExact(left, right);
        }
        catch (final Exception exception) {
            return null;
        }
    }

    public static Long subtract(final long left, final long right) {
        try {
            return Math.subtractExact(left, right);
        }
        catch (final Exception exception) {
            return null;
        }
    }

    public static Long multiply(final long left, final long right) {
        try {
            return Math.multiplyExact(left, right);
        }
        catch (final Exception exception) {
            return null;
        }
    }

    public static Long divide(final long left, final long right) {
        if ( (left == Long.MIN_VALUE) && (right == -1L) ) {
            return null;
        }

        return (left / right);
    }

    public static Long negate(final long a) {
        if (a == Long.MIN_VALUE) {
            return null;
        }

        return -a;
    }

    public static Long absoluteValue(final long a) {
        if (a == Long.MIN_VALUE) {
            return null;
        }

        if (a < 0L) { return -a; }
        return a;
    }
}

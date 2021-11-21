package com.softwareverde.bitcoin.util;

public class MathUtil {
    public static Long add(final long left, final long right) {
        if (right > 0L) {
            if (left > (Long.MAX_VALUE - right)) { return null; }
        }
        else if (left < (Long.MIN_VALUE - right)) { return null; }

        return (left + right);
    }

    public static Long subtract(final long left, final long right) {
        if (right > 0L) {
            if (left < (Long.MIN_VALUE + right)) { return null; }
        }
        else if (left > (Long.MAX_VALUE + right)) { return null; }

        return (left - right);
    }

    public static Long multiply(final long left, final long right) {
        if (right > 0L) {
            if ( (left > (Long.MAX_VALUE / right)) || (left < (Long.MIN_VALUE / right)) ) { return null; }
        }
        else if (right < -1L) {
            if ( (left > (Long.MIN_VALUE / right)) || (left < (Long.MAX_VALUE / right)) ) { return null; }
        }
        else if ( (right == -1L) && (left == Long.MIN_VALUE) ) { return null; }

        return (left * right);
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

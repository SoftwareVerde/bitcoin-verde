package com.softwareverde.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class BitcoinReflectionUtil extends ReflectionUtil {
    protected BitcoinReflectionUtil() { }

    public static void setStaticValue(final Class<?> objectClass, final String memberName, final Object value) {
        RuntimeException lastException = null;

        Class<?> clazz = objectClass;
        do {
            try {
                final Field field = clazz.getDeclaredField(memberName);
                field.setAccessible(true);

                final Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

                field.set(null, value);
                return;
            }
            catch (final NoSuchFieldException exception) {
                lastException = new RuntimeException("Invalid member name found: " + objectClass.getSimpleName() + "." + memberName);
            }
            catch (final IllegalAccessException exception) {
                lastException = new RuntimeException("Unable to access member: " + objectClass.getSimpleName() + "." + memberName);
            }
        } while ((clazz = clazz.getSuperclass()) != null);

        throw lastException;
    }

    public static void setVolatile(final Class<?> objectClass, final String memberName, final Boolean isVolatile) {
        RuntimeException lastException = null;

        Class<?> clazz = objectClass;
        do {
            try {
                final Field field = clazz.getDeclaredField(memberName);
                field.setAccessible(true);

                final Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);

                if (isVolatile) {
                    modifiersField.setInt(field, ((modifiersField.getModifiers() | Modifier.TRANSIENT) & (~Modifier.FINAL)) );
                }
                else {
                    modifiersField.setInt(field, (modifiersField.getModifiers() & (~Modifier.TRANSIENT)));
                }
                return;
            }
            catch (final NoSuchFieldException exception) {
                lastException = new RuntimeException("Invalid member name found: " + objectClass.getSimpleName() + "." + memberName);
            }
            catch (final IllegalAccessException exception) {
                lastException = new RuntimeException("Unable to access member: " + objectClass.getSimpleName() + "." + memberName);
            }
        } while ((clazz = clazz.getSuperclass()) != null);

        throw lastException;
    }
}

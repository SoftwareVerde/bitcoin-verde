package com.softwareverde.util;

import com.softwareverde.logging.Logger;

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

                try {
                    final Field modifiersField = Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                    field.set(null, value);
                }
                catch (final IllegalAccessException exception) {
                    throw exception;
                }
                catch (final Exception exception) {
                    // Java 12+ does not allow for overwriting final members;
                    //  however, this can still be achieved via Unsafe black magic.
                    // NOTE: This requires that the `--release` flag be disabled on the compiler within intelliJ.
                    //  (Preferences -> Build -> Compiler -> Java Compiler -> Disable "use --release")

                    // final sun.misc.Unsafe unsafe = sun.misc.Unsafe.getUnsafe();
                    final Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                    unsafeField.setAccessible(true);
                    final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

                    final Object staticFieldBase = unsafe.staticFieldBase(field);
                    final long staticFieldOffset = unsafe.staticFieldOffset(field);
                    unsafe.putObject(staticFieldBase, staticFieldOffset, value);
                }

                return;
            }
            catch (final NoSuchFieldException exception) {
                Logger.debug(exception);
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

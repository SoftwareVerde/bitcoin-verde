package com.softwareverde.security.pbkdf2;

import com.softwareverde.constable.bytearray.ByteArray;
import org.junit.Assert;
import org.junit.Test;

public class Pbkdf2KeyTests {
    @Test
    public void should_create_unique_key_for_passwords_greater_than_default_bit_count() {
        // Setup
        final String password0 = "act shadow acid olympic clump false come shell air define poverty session have atom quiz debate crucial size glimpse smoke around radio inner drop snack";
        final String password1 = "act shadow acid olympic clump false come shell air define poverty session have atom quiz debate crucial size glimpse smoke around radio inner drop horse";

        final ByteArray reusedSalt = Pbkdf2Key.generateRandomSalt(); // The salt is reused only to ensure the entire password is used...

        // Action
        final Pbkdf2Key pbkdf2Key0 = new Pbkdf2Key(password0, Pbkdf2Key.DEFAULT_ITERATIONS, reusedSalt, Pbkdf2Key.DEFAULT_KEY_BIT_COUNT);
        final Pbkdf2Key pbkdf2Key1 = new Pbkdf2Key(password1, Pbkdf2Key.DEFAULT_ITERATIONS, reusedSalt, Pbkdf2Key.DEFAULT_KEY_BIT_COUNT);

        // Assert
        Assert.assertNotEquals(pbkdf2Key0.getKey(), pbkdf2Key1.getKey());
    }

    @Test
    public void should_create_unique_key_for_passwords_using_non_pow2_bit_length() {
        // Setup
        final String password0 = "act shadow acid olympic clump false come shell air define poverty session have atom quiz debate crucial size glimpse smoke around radio inner drop snack";
        final String password1 = "act shadow acid olympic clump false come shell air define poverty session have atom quiz debate crucial size glimpse smoke around radio inner drop horse";

        final ByteArray reusedSalt = Pbkdf2Key.generateRandomSalt(); // The salt is reused only to ensure the entire password is used...

        // Action
        final Pbkdf2Key pbkdf2Key0 = new Pbkdf2Key(password0, Pbkdf2Key.DEFAULT_ITERATIONS, reusedSalt, 254);
        final Pbkdf2Key pbkdf2Key1 = new Pbkdf2Key(password1, Pbkdf2Key.DEFAULT_ITERATIONS, reusedSalt, 248); // 254 gets truncated to 248 bits...

        // Assert
        Assert.assertNotEquals(pbkdf2Key0.getKey(), pbkdf2Key1.getKey());
    }
}

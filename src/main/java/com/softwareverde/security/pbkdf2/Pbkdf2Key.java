package com.softwareverde.security.pbkdf2;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public class Pbkdf2Key implements Const {
    private static final SecretKeyFactory SECRET_KEY_FACTORY;
    static {
        try {
            SECRET_KEY_FACTORY = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        }
        catch (final Exception exception) {
            throw new RuntimeException("Pbkdf2Key algorithm not available.", exception);
        }
    }

    protected static synchronized SecretKey _generateSecretKey(final PBEKeySpec pbeKeySpec) throws InvalidKeySpecException {
        return SECRET_KEY_FACTORY.generateSecret(pbeKeySpec);
    }

    /**
     * Generates a new, random 256-bit salt.
     */
    public static ByteArray generateRandomSalt() {
        final SecureRandom secureRandom = new SecureRandom();
        final MutableByteArray salt = new MutableByteArray(DEFAULT_SALT_BYTE_COUNT);
        secureRandom.nextBytes(salt.unwrap());
        return salt;
    }

    public static final Integer DEFAULT_ITERATIONS = (1 << 16);
    public static final Integer DEFAULT_SALT_BYTE_COUNT = 32;
    public static final Integer DEFAULT_KEY_BIT_COUNT = 256;

    protected final ByteArray _key;
    protected final ByteArray _salt;
    protected final Integer _iterations;

    /**
     * Stretches the provided password into a DEFAULT_KEY_BIT_COUNT-bit key using default parameters and a random salt.
     */
    public Pbkdf2Key(final String password) {
        this(password, DEFAULT_ITERATIONS, Pbkdf2Key.generateRandomSalt(), DEFAULT_KEY_BIT_COUNT);
    }

    /**
     * Stretches the provided password into a keyLength-bit key using default parameters and a random salt.
     */
    public Pbkdf2Key(final String password, final Integer keyBitCount) {
        this(password, DEFAULT_ITERATIONS, Pbkdf2Key.generateRandomSalt(), keyBitCount);
    }

    public Pbkdf2Key(final String password, final Integer iterations, final ByteArray salt, final Integer keyBitCount) {
        try {
            final PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), iterations, keyBitCount);
            final SecretKey key = _generateSecretKey(pbeKeySpec);

            _key = MutableByteArray.wrap(key.getEncoded());
            _salt = salt;
            _iterations = iterations;
        }
        catch (final Exception exception) {
            throw new RuntimeException("Unable to generate Pbkdf2 key.", exception);
        }
    }

    public ByteArray getKey() {
        return _key;
    }

    public ByteArray getSalt() {
        return _salt;
    }

    public Integer getIterations() {
        return _iterations;
    }

    public Integer getKeyBitCount() {
        return (_key.getByteCount() * 8);
    }
}

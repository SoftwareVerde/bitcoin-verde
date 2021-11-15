package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.util.Base32Util;
import com.softwareverde.util.Base58Util;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.timer.MilliTimer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AddressModule {

    public static void execute(final String desiredAddressPrefix, final Boolean ignoreCase) {
        final boolean useBase32 = true;

        final AddressInflater addressInflater = new AddressInflater();

        final String desiredAddressPrefixLowerCase = desiredAddressPrefix.toLowerCase();

        { // Ensure the address is possible...
            for (final char desiredCharacter : desiredAddressPrefix.toCharArray()) {
                boolean hasMatch = false;
                for (final char availableCharacter : (useBase32 ? Base32Util.ALPHABET.toCharArray() : Base58Util.ALPHABET)) {
                    if ( (availableCharacter == desiredCharacter) || (ignoreCase && (Character.toLowerCase(availableCharacter) == Character.toLowerCase(desiredCharacter)))) {
                        hasMatch = true;
                        break;
                    }
                }
                if (! hasMatch) {
                    System.out.println("Desired prefix is not possible.");
                    return;
                }
            }
        }

        final AtomicLong hashCount = new AtomicLong(0L);
        final MilliTimer milliTimer = new MilliTimer();

        final Thread readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (! Thread.interrupted()) {
                    try {
                        System.in.read(new byte[1]);
                        milliTimer.stop();

                        System.out.println(hashCount.get() + " hashes in " + milliTimer.getMillisecondsElapsed() + "ms. (" + ((hashCount.get() * 1000L) / milliTimer.getMillisecondsElapsed()) + "H/s)");
                    }
                    catch (final Exception exception) { break; }
                }
            }
        });
        readThread.start();

        milliTimer.start();

        final AtomicBoolean miningPin = new AtomicBoolean(true);

        final Runnable addressMiner = new Runnable() {
            @Override
            public void run() {
                while ( (! Thread.interrupted()) && miningPin.get() ) {
                    final PrivateKey privateKey = PrivateKey.createNewKey();

                    final String address;
                    final String compressedAddress;
                    if (useBase32) {
                        address = addressInflater.fromPrivateKey(privateKey, false).toBase32CheckEncoded(false);
                        compressedAddress = addressInflater.fromPrivateKey(privateKey, true).toBase32CheckEncoded(false);
                    }
                    else {
                        address = addressInflater.fromPrivateKey(privateKey, false).toBase58CheckEncoded();
                        compressedAddress = addressInflater.fromPrivateKey(privateKey, true).toBase58CheckEncoded();
                    }

                    boolean isMatch = false;
                    isMatch = (isMatch || address.startsWith(desiredAddressPrefix));
                    isMatch = (isMatch || compressedAddress.startsWith(desiredAddressPrefix));
                    if (ignoreCase) {
                        isMatch = (isMatch || address.toLowerCase().startsWith(desiredAddressPrefixLowerCase));
                        isMatch = (isMatch || compressedAddress.toLowerCase().startsWith(desiredAddressPrefixLowerCase));
                    }

                    hashCount.addAndGet(2L);

                    if (isMatch) {
                        System.out.println("Private Key                 : " + HexUtil.toHexString(privateKey.getBytes()));
                        System.out.println("Public Key                  : " + privateKey.getPublicKey());
                        System.out.println("Bitcoin Address             : " + address);
                        System.out.println("Compressed Bitcoin Address  : " + compressedAddress);
                        miningPin.set(false);
                    }
                }
            }
        };

        final int threadCount = 3;
        final Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; ++i) {
            threads[i] = new Thread(addressMiner);
            threads[i].setPriority(Thread.MAX_PRIORITY);
            threads[i].start();
        }

        for (int i = 0; i < threadCount; ++i) {
            try { threads[i].join(); } catch (final Exception exception) { }
        }

        readThread.interrupt();
        try { readThread.join(); } catch (final Exception exception) { }
    }
}

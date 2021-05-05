package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.secp256k1.signature.BitcoinMessageSignature;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.wallet.SeedPhraseGenerator;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;

public class SignatureModule {
    protected static SeedPhraseGenerator getSeedPhraseGenerator() {
        final String seedWords = IoUtil.getResource("/seed_words/seed_words_english.txt");
        final ImmutableListBuilder<String> seedWordsBuilder = new ImmutableListBuilder<String>(2048);
        for (final String seedWord : seedWords.split("\n")) {
            seedWordsBuilder.add(seedWord.trim());
        }

        if (seedWordsBuilder.getCount() != 2048) {
            Logger.error("Unable to load seed phrase word list.");
            return null;
        }

        return new SeedPhraseGenerator(seedWordsBuilder.build());
    }

    protected static PrivateKey parsePrivateKeyFromFileContents(final String keyFileContents) {
        final PrivateKey privateKeyViaHexString = PrivateKey.fromHexString(keyFileContents);
        if (privateKeyViaHexString != null) { return privateKeyViaHexString; }

        final SeedPhraseGenerator seedPhraseGenerator = SignatureModule.getSeedPhraseGenerator();
        if (seedPhraseGenerator == null) { return null; }

        final Boolean isSeedPhrase = seedPhraseGenerator.isSeedPhraseValid(keyFileContents);
        if (! isSeedPhrase) { return null; }

        final ByteArray privateKeyBytes = seedPhraseGenerator.fromSeedPhrase(keyFileContents);
        return PrivateKey.fromBytes(privateKeyBytes);
    }

    public static void executeSign(final String keyFileName, final String message, final Boolean useCompressedAddress) {
        final String keyFileContents;
        {
            final byte[] bytes = IoUtil.getFileContents(keyFileName);
            if (bytes == null) {
                Logger.error("Unable to read key file '" + keyFileName + "'.");
                return;
            }

            keyFileContents = StringUtil.bytesToString(bytes).trim();

            if (keyFileContents.isEmpty()) {
                Logger.error("Empty key file.");
                return;
            }
        }

        final PrivateKey privateKey = SignatureModule.parsePrivateKeyFromFileContents(keyFileContents);
        if (privateKey == null) {
            Logger.error("Unrecognized key format.");
            return;
        }

        final BitcoinMessageSignature signature = BitcoinUtil.signBitcoinMessage(privateKey, message, useCompressedAddress);
        if (signature == null) {
            Logger.error("Unable to sign message.");
            return;
        }

        final AddressInflater addressInflater = new AddressInflater();

        System.out.println("Address:    " + addressInflater.fromPrivateKey(privateKey, useCompressedAddress));
        System.out.println("Signature:  " + signature.toBase64());
        System.out.println("Message:    " + message);
    }

    public static void executeVerify(final String addressStringBase58Check, final String signatureStringBase64, final String message) {
        final AddressInflater addressInflater = new AddressInflater();

        final BitcoinMessageSignature signature = BitcoinMessageSignature.fromBase64(signatureStringBase64);
        if (signature == null) {
            Logger.error("Invalid signature.");
            return;
        }

        final Address address = addressInflater.fromBase58Check(addressStringBase58Check);
        if (address == null) {
            Logger.error("Invalid address.");
            return;
        }

        final Boolean signatureIsValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);
        System.out.println("Address:    " + address.toBase58CheckEncoded());
        System.out.println("Signature:  " + signature.toBase64());
        System.out.println("Message:    " + message);
        System.out.println("------------");
        System.out.println("Is Valid:   " + (signatureIsValid ? "1" : "0"));
    }

    protected SignatureModule() { }
}

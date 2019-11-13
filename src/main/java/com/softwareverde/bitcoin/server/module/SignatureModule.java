package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.wallet.SeedPhraseGenerator;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
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

    public static void execute(final String keyFileName, final String message) {
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

        final byte[] preImageHash = BitcoinUtil.sha256(BitcoinUtil.sha256(message.getBytes()));
        final Signature signature = Secp256k1.sign(privateKey, preImageHash);
        final PublicKey publicKey = privateKey.getPublicKey();

        if (message.contains("\n")) {
            System.out.print("Message:\n==========\n");
            System.out.println(message);
            System.out.print("==========\n");
        }
        else {
            System.out.println("Message     : " + message);
        }

        System.out.println("Public Key  : " + publicKey);
        System.out.println("Signature   : " + signature);
    }
}

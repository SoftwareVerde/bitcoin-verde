package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.util.HexUtil;

public class AddressModule {
    public static void execute() {
        final AddressInflater addressInflater = new AddressInflater();

        final PrivateKey privateKey = PrivateKey.createNewKey();
        System.out.println("Private Key                 : " + HexUtil.toHexString(privateKey.getBytes()));
        System.out.println("Public Key                  : " + privateKey.getPublicKey());
        System.out.println("Bitcoin Address             : " + addressInflater.fromPrivateKey(privateKey).toBase58CheckEncoded());
        System.out.println("Compressed Bitcoin Address  : " + addressInflater.compressedFromPrivateKey(privateKey).toBase58CheckEncoded());
    }
}

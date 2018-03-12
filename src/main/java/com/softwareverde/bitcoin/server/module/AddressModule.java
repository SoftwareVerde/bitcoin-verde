package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;

public class AddressModule {
    public static void execute() {
        final PrivateKey privateKey = PrivateKey.createNewKey();
        System.out.println("Private Key     : " + BitcoinUtil.toHexString(privateKey.getBytes()));
        System.out.println("Public Key      : " + privateKey.getPublicKey());
        System.out.println("Bitcoin Address : " + Address.fromPrivateKey(privateKey).toBase58CheckEncoded());
    }
}

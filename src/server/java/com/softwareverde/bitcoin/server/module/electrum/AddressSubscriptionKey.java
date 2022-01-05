package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

class AddressSubscriptionKey {
    public final Sha256Hash scriptHash;
    public final String subscriptionString;
    public final Boolean isScriptHash;

    public AddressSubscriptionKey(final Address address, final String subscriptionString) {
        this.scriptHash = ScriptBuilder.computeScriptHash(address);
        this.subscriptionString = subscriptionString;
        this.isScriptHash = false;
    }

    public AddressSubscriptionKey(final Sha256Hash scriptHash, final String subscriptionString) {
        this.scriptHash = scriptHash;
        this.subscriptionString = subscriptionString;
        this.isScriptHash = true;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof AddressSubscriptionKey)) { return false; }
        final AddressSubscriptionKey addressSubscriptionKey = (AddressSubscriptionKey) object;
        return Util.areEqual(this.scriptHash, addressSubscriptionKey.scriptHash);
    }

    @Override
    public int hashCode() {
        return this.scriptHash.hashCode();
    }

    @Override
    public String toString() {
        return this.scriptHash + ":" + (this.isScriptHash ? "Hash" : "Address");
    }
}

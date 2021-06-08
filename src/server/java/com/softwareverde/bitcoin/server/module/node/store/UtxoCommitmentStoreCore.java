package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;

public class UtxoCommitmentStoreCore implements UtxoCommitmentStore {
    protected final String _dataDirectory;
    protected final String _blockDataDirectory;

    public UtxoCommitmentStoreCore(final String dataDirectory) {
        _dataDirectory = dataDirectory;
        _blockDataDirectory = (dataDirectory != null ? (dataDirectory + "/" + BitcoinProperties.DATA_DIRECTORY_NAME + "/utxo") : null);
    }

    @Override
    public Boolean storeUtxoCommitment(final PublicKey publicKey, final ByteArray utxoCommitment) {
        return null;
    }

    @Override
    public void removeUtxoCommitment(final PublicKey publicKey) {

    }

    @Override
    public ByteArray getUtxoCommitment(final PublicKey publicKey) {
        return null;
    }

    @Override
    public Boolean blockExists(final PublicKey publicKey) {
        return null;
    }

    @Override
    public String getDataDirectory() {
        return null;
    }
}

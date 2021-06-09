package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.IoUtil;

import java.io.File;

public class UtxoCommitmentStoreCore implements UtxoCommitmentStore {
    protected final String _dataDirectory;
    protected final String _utxoDataDirectory;

    public UtxoCommitmentStoreCore(final String dataDirectory) {
        _dataDirectory = dataDirectory;
        _utxoDataDirectory = (dataDirectory != null ? (dataDirectory + "/" + BitcoinProperties.DATA_DIRECTORY_NAME + "/utxo") : null);
    }

    @Override
    public File storeUtxoCommitment(final PublicKey publicKey, final ByteArray utxoCommitment) {
        final String publicKeyString = publicKey.toString();
        final File file = new File(_utxoDataDirectory, publicKeyString);
        final Boolean wasSuccessful = IoUtil.putFileContents(file, utxoCommitment);
        if (! wasSuccessful) { return null; }
        return file;
    }

    @Override
    public void removeUtxoCommitment(final PublicKey publicKey) {
        final String publicKeyString = publicKey.toString();
        final File file = new File(_utxoDataDirectory, publicKeyString);
        file.delete();
    }

    @Override
    public ByteArray getUtxoCommitment(final PublicKey publicKey) {
        final String publicKeyString = publicKey.toString();
        final File file = new File(_utxoDataDirectory, publicKeyString);
        return ByteArray.wrap(IoUtil.getFileContents(file));
    }

    @Override
    public Boolean blockExists(final PublicKey publicKey) {
        final String publicKeyString = publicKey.toString();
        final File file = new File(_utxoDataDirectory, publicKeyString);
        return file.exists();
    }

    @Override
    public String getDataDirectory() {
        return _dataDirectory;
    }

    @Override
    public String getUtxoDataDirectory() {
        return _utxoDataDirectory;
    }
}

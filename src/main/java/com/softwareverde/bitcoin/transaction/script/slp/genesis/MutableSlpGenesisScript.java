package com.softwareverde.bitcoin.transaction.script.slp.genesis;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;

public class MutableSlpGenesisScript extends SlpGenesisScriptCore {

    public void setTokenAbbreviation(final String tokenAbbreviation) {
        _tokenAbbreviation = tokenAbbreviation;
    }

    public void setTokenName(final String tokenName) {
        _tokenName = tokenName;
    }

    public void setDocumentUrl(final String documentUrl) {
        _documentUrl = documentUrl;
    }

    public void setDocumentHash(final Sha256Hash documentHash) {
        _documentHash = documentHash;
    }

    public void setDecimalCount(final Integer decimalCount) {
        _decimalCount = decimalCount;
    }

    public void setGeneratorOutputIndex(final Integer generatorOutputIndex) {
        _generatorOutputIndex = generatorOutputIndex;
    }

    /**
     * Sets the number of tokens created.
     *  The tokenCount is the equivalent to the number of "satoshis" created, not the number of "bitcoin".
     */
    public void setTokenCount(final Long tokenCount) {
        _tokenCount = tokenCount;
    }

    @Override
    public ImmutableSlpGenesisScript asConst() {
        return new ImmutableSlpGenesisScript(this);
    }
}

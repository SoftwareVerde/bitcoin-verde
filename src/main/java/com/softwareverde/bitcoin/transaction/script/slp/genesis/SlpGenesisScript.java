package com.softwareverde.bitcoin.transaction.script.slp.genesis;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.constable.Constable;
import com.softwareverde.util.Util;

public interface SlpGenesisScript extends SlpScript, Constable<ImmutableSlpGenesisScript> {
    Integer RECEIVER_TRANSACTION_OUTPUT_INDEX = 1;

    String getTokenAbbreviation();
    String getTokenName();
    String getDocumentUrl();
    Sha256Hash getDocumentHash();
    Integer getDecimalCount();
    Integer getGeneratorOutputIndex();
    Long getTokenCount();

    @Override
    ImmutableSlpGenesisScript asConst();
}

abstract class SlpGenesisScriptCore implements SlpGenesisScript {
    protected String _tokenAbbreviation;
    protected String _tokenName;
    protected String _documentUrl = null;
    protected Sha256Hash _documentHash = null;
    protected Integer _decimalCount = 8;
    protected Integer _generatorOutputIndex;
    protected Long _tokenCount = 21000000L;

    public SlpGenesisScriptCore() { }

    public SlpGenesisScriptCore(final SlpGenesisScript slpGenesisScript) {
        _tokenAbbreviation = slpGenesisScript.getTokenAbbreviation();
        _tokenName = slpGenesisScript.getTokenName();
        _documentUrl = slpGenesisScript.getDocumentUrl();
        _documentHash = slpGenesisScript.getDocumentHash();
        _decimalCount = slpGenesisScript.getDecimalCount();
        _generatorOutputIndex = slpGenesisScript.getGeneratorOutputIndex();
        _tokenCount = slpGenesisScript.getTokenCount();
    }

    @Override
    public String getTokenAbbreviation() {
        return _tokenAbbreviation;
    }

    @Override
    public String getTokenName() {
        return _tokenName;
    }

    @Override
    public String getDocumentUrl() {
        return _documentUrl;
    }

    @Override
    public Sha256Hash getDocumentHash() {
        return _documentHash;
    }

    @Override
    public Integer getDecimalCount() {
        return _decimalCount;
    }

    @Override
    public Integer getGeneratorOutputIndex() {
        return _generatorOutputIndex;
    }

    @Override
    public Long getTokenCount() {
        return _tokenCount;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof SlpGenesisScript)) { return false; }

        final SlpGenesisScript slpGenesisScript = (SlpGenesisScript) object;
        if (! Util.areEqual(_tokenAbbreviation, slpGenesisScript.getTokenAbbreviation())) { return false; }
        if (! Util.areEqual(_tokenName, slpGenesisScript.getTokenName())) { return false; }
        if (! Util.areEqual(_documentUrl, slpGenesisScript.getDocumentUrl())) { return false; }
        if (! Util.areEqual(_documentHash, slpGenesisScript.getDocumentHash())) { return false; }
        if (! Util.areEqual(_decimalCount, slpGenesisScript.getDecimalCount())) { return false; }
        if (! Util.areEqual(_generatorOutputIndex, slpGenesisScript.getGeneratorOutputIndex())) { return false; }
        if (! Util.areEqual(_tokenCount, slpGenesisScript.getTokenCount())) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = Util.coalesce(_tokenAbbreviation).hashCode();
        hashCode += Util.coalesce(_tokenName).hashCode();
        hashCode += Util.coalesce(_documentUrl).hashCode();
        hashCode += Util.coalesce(_documentHash, Sha256Hash.EMPTY_HASH).hashCode();
        hashCode += Util.coalesce(_decimalCount).hashCode();
        hashCode += Util.coalesce(_generatorOutputIndex).hashCode();
        hashCode += Util.coalesce(_tokenCount).hashCode();
        return hashCode;
    }
}
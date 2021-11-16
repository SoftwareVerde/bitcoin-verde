package com.softwareverde.bitcoin.transaction.script.slp.genesis;

import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptType;
import com.softwareverde.constable.Constable;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.Util;

import java.math.BigInteger;

public interface SlpGenesisScript extends SlpScript, Jsonable, Constable<ImmutableSlpGenesisScript> {
    Integer RECEIVER_TRANSACTION_OUTPUT_INDEX = 1;

    String getTokenAbbreviation();
    String getTokenName();
    String getDocumentUrl();
    Sha256Hash getDocumentHash();
    Integer getDecimalCount();
    Integer getBatonOutputIndex();
    BigInteger getTokenCount();

    @Override
    ImmutableSlpGenesisScript asConst();
}

abstract class SlpGenesisScriptCore implements SlpGenesisScript {
    protected String _tokenAbbreviation;
    protected String _tokenName;
    protected String _documentUrl = null;
    protected Sha256Hash _documentHash = null;
    protected Integer _decimalCount = 8;
    protected Integer _batonOutputIndex;
    protected BigInteger _tokenCount = BigInteger.valueOf(21000000L);

    public SlpGenesisScriptCore() { }

    public SlpGenesisScriptCore(final SlpGenesisScript slpGenesisScript) {
        _tokenAbbreviation = slpGenesisScript.getTokenAbbreviation();
        _tokenName = slpGenesisScript.getTokenName();
        _documentUrl = slpGenesisScript.getDocumentUrl();
        _documentHash = slpGenesisScript.getDocumentHash();
        _decimalCount = slpGenesisScript.getDecimalCount();
        _batonOutputIndex = slpGenesisScript.getBatonOutputIndex();
        _tokenCount = slpGenesisScript.getTokenCount();
    }

    @Override
    public SlpScriptType getType() {
        return SlpScriptType.GENESIS;
    }

    @Override
    public Integer getMinimumTransactionOutputCount() {
        return Math.max(2, (Util.coalesce(_batonOutputIndex) + 1)); // Requires at least 1 Script Output and 1 Receiver Output...
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
    public Integer getBatonOutputIndex() {
        return _batonOutputIndex;
    }

    @Override
    public BigInteger getTokenCount() {
        return _tokenCount;
    }

    @Override
    public Json toJson() {
        final Json json = new Json(false);

        json.put("tokenAbbreviation", _tokenAbbreviation);
        json.put("tokenName", _tokenName);
        json.put("documentUrl", _documentUrl);
        json.put("documentHash", _documentHash);
        json.put("decimalCount", _decimalCount);
        json.put("batonIndex", _batonOutputIndex);
        json.put("tokenCount", _tokenCount.toString());

        return json;
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
        if (! Util.areEqual(_batonOutputIndex, slpGenesisScript.getBatonOutputIndex())) { return false; }
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
        hashCode += Util.coalesce(_batonOutputIndex).hashCode();
        hashCode += Util.coalesce(_tokenCount, BigInteger.ZERO).hashCode();
        return hashCode;
    }
}
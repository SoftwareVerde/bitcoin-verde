package com.softwareverde.bitcoin.wallet.slp;

import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.wallet.utxo.ImmutableSpendableTransactionOutput;
import com.softwareverde.bitcoin.wallet.utxo.SpendableTransactionOutput;

import java.math.BigInteger;

public class ImmutableSlpToken extends ImmutableSpendableTransactionOutput implements SlpToken {
    protected final SlpTokenId _tokenId;
    protected final BigInteger _tokenAmount;
    protected final Boolean _isBatonHolder;

    public ImmutableSlpToken(final SlpTokenId tokenId, final BigInteger tokenAmount, final SpendableTransactionOutput spendableTransactionOutput) {
        super(spendableTransactionOutput);
        _tokenId = tokenId;
        _tokenAmount = tokenAmount;
        _isBatonHolder = false;
    }

    public ImmutableSlpToken(final SlpTokenId tokenId, final BigInteger tokenAmount, final SpendableTransactionOutput spendableTransactionOutput, final Boolean isBatonHolder) {
        super(spendableTransactionOutput);
        _tokenId = tokenId;
        _tokenAmount = tokenAmount;
        _isBatonHolder = isBatonHolder;
    }

    @Override
    public SlpTokenId getTokenId() {
        return _tokenId;
    }

    @Override
    public BigInteger getTokenAmount() {
        return _tokenAmount;
    }

    @Override
    public Boolean isBatonHolder() {
        return _isBatonHolder;
    }

    @Override
    public ImmutableSlpToken asConst() {
        return this;
    }
}

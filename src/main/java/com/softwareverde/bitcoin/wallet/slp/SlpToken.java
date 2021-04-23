package com.softwareverde.bitcoin.wallet.slp;

import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.wallet.utxo.SpendableTransactionOutput;

import java.math.BigInteger;

public interface SlpToken extends SpendableTransactionOutput {
    SlpTokenId getTokenId();
    BigInteger getTokenAmount();
    Boolean isBatonHolder();
}

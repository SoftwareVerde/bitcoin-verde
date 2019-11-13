package com.softwareverde.bitcoin.wallet.slp;

import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.wallet.utxo.SpendableTransactionOutput;

public interface SlpToken extends SpendableTransactionOutput {
    SlpTokenId getTokenId();
    Long getTokenAmount();
    Boolean isBatonHolder();
}

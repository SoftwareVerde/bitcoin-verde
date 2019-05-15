package com.softwareverde.bitcoin.transaction.coinbase;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;

public interface CoinbaseTransaction extends Transaction {
    UnlockingScript getCoinbaseScript();
    Long getBlockReward();
}

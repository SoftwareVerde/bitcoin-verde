package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.wallet.utxo.SpendableTransactionOutput;
import com.softwareverde.constable.list.List;

public class TransactionBundle {
    public List<PaymentAmount> paymentAmountsWithChange;
    public List<SpendableTransactionOutput> transactionOutputsToSpend;
    public LockingScript opReturnScript;
    public long expectedFees;
}

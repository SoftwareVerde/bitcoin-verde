package com.softwareverde.bitcoin.transaction.script.runner.context;

import com.softwareverde.json.Json;

public class ContextDeflater {
    public Json toJson(final TransactionContext transactionContext) {
        final Json json = new Json();

        json.put("blockHeight", transactionContext.getBlockHeight());
        json.put("medianBlockTime", transactionContext.getMedianBlockTime());
        json.put("transaction", transactionContext.getTransaction());
        json.put("transactionInputIndex", transactionContext.getTransactionInputIndex());
        json.put("transactionInput", transactionContext.getTransactionInput());
        json.put("transactionOutput", transactionContext.getTransactionOutputBeingSpent());

        json.put("currentScript", transactionContext.getCurrentScript());
        json.put("scriptIndex", transactionContext.getScriptIndex());
        json.put("scriptLastCodeSeparatorIndex", transactionContext.getScriptLastCodeSeparatorIndex());
        json.put("signatureOperationCount", transactionContext.getSignatureOperationCount());

        return json;
    }
}

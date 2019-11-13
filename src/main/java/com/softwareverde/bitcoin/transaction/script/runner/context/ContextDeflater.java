package com.softwareverde.bitcoin.transaction.script.runner.context;

import com.softwareverde.json.Json;

public class ContextDeflater {
    public Json toJson(final Context context) {
        final Json json = new Json();

        json.put("blockHeight", context.getBlockHeight());
        json.put("medianBlockTime", context.getMedianBlockTime());
        json.put("transaction", context.getTransaction());
        json.put("transactionInputIndex", context.getTransactionInputIndex());
        json.put("transactionInput", context.getTransactionInput());
        json.put("transactionOutput", context.getTransactionOutput());

        json.put("currentScript", context.getCurrentScript());
        json.put("scriptIndex", context.getScriptIndex());
        json.put("scriptLastCodeSeparatorIndex", context.getScriptLastCodeSeparatorIndex());

        return json;
    }
}

package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;

public class BlockValidationResult extends ValidationResult {
    public static BlockValidationResult valid() {
        return new BlockValidationResult(true, null, null);
    }

    public static BlockValidationResult invalid(final String errorMessage) {
        return new BlockValidationResult(false, errorMessage, new MutableList<>());
    }

    public static BlockValidationResult invalid(final String errorMessage, final Transaction invalidTransaction) {
        final ImmutableListBuilder<Sha256Hash> invalidTransactions = new ImmutableListBuilder<>(1);
        invalidTransactions.add(invalidTransaction.getHash());

        return new BlockValidationResult(false, errorMessage, invalidTransactions.build());
    }

    public static BlockValidationResult invalid(final String errorMessage, final List<Sha256Hash> invalidTransactions) {
        return new BlockValidationResult(false, errorMessage, invalidTransactions);
    }

    public final List<Sha256Hash> invalidTransactions;

    public BlockValidationResult(final Boolean isValid, final String errorMessage, final List<Sha256Hash> invalidTransactions) {
        super(isValid, errorMessage);
        this.invalidTransactions = ConstUtil.asConstOrNull(invalidTransactions);
    }

    @Override
    public Json toJson() {
        final Json json = super.toJson();

        final Json invalidTransactionsJson;
        if (this.invalidTransactions != null) {
            invalidTransactionsJson = new Json(true);
            for (final Sha256Hash transactionHash : this.invalidTransactions) {
                invalidTransactionsJson.add(transactionHash);
            }
        }
        else {
            invalidTransactionsJson = null;
        }

        json.put("invalidTransactions", invalidTransactionsJson);

        return json;
    }
}

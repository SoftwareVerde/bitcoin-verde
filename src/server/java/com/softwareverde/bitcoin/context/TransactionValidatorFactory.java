package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;

public interface TransactionValidatorFactory {
    TransactionValidator getTransactionValidator(BlockOutputs blockOutputs, TransactionValidator.Context transactionValidatorContext);

    default TransactionValidator getUnconfirmedTransactionValidator(final TransactionValidator.Context transactionValidatorContext) {
        return this.getTransactionValidator(null, transactionValidatorContext);
    }
}

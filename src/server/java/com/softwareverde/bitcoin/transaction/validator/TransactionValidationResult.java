package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.validator.ValidationResult;
import com.softwareverde.json.Json;

public class TransactionValidationResult extends ValidationResult {

    public static TransactionValidationResult valid(final Integer signatureOperationCount) {
        return new TransactionValidationResult(true, null, signatureOperationCount);
    }

    public static TransactionValidationResult invalid(final String errorMessage) {
        return new TransactionValidationResult(false, errorMessage, null);
    }

    public static TransactionValidationResult invalid(final Json errorMessage) {
        return new TransactionValidationResult(false, ((errorMessage != null) ? errorMessage.toString() : null), null);
    }

    /**
     * The number of signature operations executed by this Transaction, or null if validation failed.
     */
    public final Integer signatureOperationCount;

    public TransactionValidationResult(final Boolean isValid, final String errorMessage, final Integer signatureOperationCount) {
        super(isValid, errorMessage);
        this.signatureOperationCount = signatureOperationCount;
    }
}

package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

public class ValidationResult implements Jsonable {
    public static ValidationResult valid() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult invalid(final String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }

    public final Boolean isValid;
    public final String errorMessage;

    public ValidationResult(final Boolean isValid, final String errorMessage) {
        this.isValid = isValid;
        this.errorMessage = errorMessage;
    }

    @Override
    public Json toJson() {
        final Json json = new Json();

        json.put("isValid", this.isValid);
        json.put("errorMessage", this.errorMessage);

        return json;
    }
}

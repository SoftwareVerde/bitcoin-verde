package com.softwareverde.bitcoin.server.stratum.message;

import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

public class ResponseMessage implements Jsonable {
    public enum Error {
        OTHER(20, "Other/Unknown"),
        NOT_FOUND(21, "Job not found"),
        DUPLICATE(22, "Duplicate share"),
        LOW_DIFFICULTY(23, "Low difficulty share"),
        UNAUTHORIZED(24, "Unauthorized worker"),
        NOT_SUBSCRIBED(25, "Not subscribed");

        public static Error fromCode(final int code) {
            for (final Error error : Error.values()) {
                if (error.code == code) {
                    return error;
                }
            }
            return Error.OTHER;
        }

        public final int code;
        public final String message;

        Error(final int code, final String message) {
            this.code = code;
            this.message = message;
        }
    }

    public static ResponseMessage parse(final String input) {
        final Json json = Json.parse(input);
        return ResponseMessage.parse(json);
    }

    public static ResponseMessage parse(final Json json) {
        if (json.isArray()) { return null; }
        if (! json.hasKey("result")) { return null; }

        final Integer id = json.getInteger("id");
        final ResponseMessage responseMessage = new ResponseMessage(id);

        {
            final boolean isBoolean = (! Json.isJson(json.getString("result")));
            if (isBoolean) {
                responseMessage._result = (json.getBoolean("result") ? RESULT_TRUE : RESULT_FALSE);
            }
            else {
                responseMessage._result = json.get("result");
            }
        }

        {
            final Json errors = json.get("error");
            if (errors != null) {
                responseMessage._rawError = errors;

                final int length = errors.length();
                if (length > 0) {
                    final int code = errors.getInteger(0);
                    responseMessage._error = Error.fromCode(code);
                }
            }

        }

        return responseMessage;
    }

    public final static Json RESULT_TRUE = new Json();
    public final static Json RESULT_FALSE = new Json();

    protected final Integer _id;
    protected Json _result = RESULT_FALSE;
    protected Error _error = null;
    protected Json _rawError = null;

    public ResponseMessage(final Integer id) {
        _id = id;
    }

    public Integer getId() {
        return _id;
    }

    public void setResult(final Json result) {
        _result = result;
    }

    public void setError(final Error error) {
        _error = error;
    }

    public Json getRawError() {
        return _rawError;
    }

    @Override
    public Json toJson() {
        final Json message = new Json(false);
        message.put("id", _id);

        if (_result == RESULT_TRUE) {
            message.put("result", true);
        }
        else if (_result == RESULT_FALSE) {
            message.put("result", false);
        }
        else {
            message.put("result", _result);
        }

        if (_error == null) {
            message.put("error", null);
        }
        else {
            final Json errors = new Json(true);
            errors.add(_error.code);
            errors.add(_error.message);
            errors.add(null);

            message.put("error", errors);
        }

        return message;
    }

    @Override
    public String toString() {
        final Json json = this.toJson();
        return json.toString();
    }
}

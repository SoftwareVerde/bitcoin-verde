package com.softwareverde.bitcoin.server.stratum.message;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

public class ResponseMessage implements Jsonable {
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
            final Boolean isBoolean = (! Json.isJson(json.getString("result")));
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
                for (int i = 0; i < errors.length(); ++i) {
                    final String error = errors.getString(i);
                    responseMessage._error.add(error);
                }
            }
        }

        return responseMessage;
    }

    public final static Json RESULT_TRUE = new Json();
    public final static Json RESULT_FALSE = new Json();

    protected final Integer _id;
    protected Json _result = RESULT_FALSE;
    protected final MutableList<String> _error = new MutableList<String>();

    public ResponseMessage(final Integer id) {
        _id = id;
    }

    public Integer getId() {
        return _id;
    }

    public void setResult(final Json result) {
        _result = result;
    }

    public void setError(final String error1, final String error2, final String error3) {
        _error.clear();
        _error.add(error1);
        _error.add(error2);
        _error.add(error3);
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

        if (_error.isEmpty()) {
            message.put("error", null);
        }
        else {
            final Json errors = new Json(true);
            for (final String error : _error) {
                errors.add(error);
            }
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

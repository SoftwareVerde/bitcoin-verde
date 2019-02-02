package com.softwareverde.bitcoin.server.stratum.message;

import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

public class RequestMessage implements Jsonable {
    private static int _nextId = 1;
    private static final Object _mutex = new Object();

    private static int createId() {
        synchronized (_mutex) {
            final int id = _nextId;
            _nextId += 1;
            return id;
        }
    }

    private static void synchronizeNextId(final Integer id) {
        if (id == null) { return; }

        synchronized (_mutex) {
            if (_nextId < id) {
                _nextId = (id + 1);
            }
        }
    }

    public enum ClientCommand {
        AUTHORIZE("mining.authorize"),
        CAPABILITIES("mining.capabilities"),
        EXTRA_NONCE("mining.extranonce.subscribe"),
        GET_TRANSACTIONS("mining.get_transactions"),
        SUBMIT("mining.submit"),
        SUBSCRIBE("mining.subscribe"),
        SUGGEST_DIFFICULTY("mining.suggest_difficulty"),
        SUGGEST_TARGET("mining.suggest_target");

        private final String _value;
        ClientCommand(final String value) { _value = value; }
        public String getValue() { return _value; }
    }

    public enum ServerCommand {
        GET_VERSION("client.get_version"),
        RECONNECT("client.reconnect"),
        SHOW_MESSAGES("client.show_messages"),
        NOTIFY("mining.notify"),
        SET_DIFFICULTY("mining.set_difficulty"),
        SET_EXTRA_NONCE("mining.set_extranonce"),
        SET_GOAL("mining.set_goal");

        private final String _value;
        ServerCommand(final String value) { _value = value; }
        public String getValue() { return _value; }
    }

    public static RequestMessage parse(final String input) {
        final Json json = Json.parse(input);
        return RequestMessage.parse(json);
    }

    public static RequestMessage parse(final Json json) {
        if (json.isArray()) { return null; }
        if (! json.hasKey("method")) { return null; }

        final Integer id = json.getInteger("id");
        final String command = json.getString("method");

        final RequestMessage requestMessage = new RequestMessage(id, command);
        requestMessage._parameters = json.get("params");

        return requestMessage;
    }

    protected final Integer _id;
    protected final String _command;
    protected Json _parameters = new Json(true);

    protected RequestMessage(final Integer id, final String command) {
        synchronizeNextId(id);

        _id = id;
        _command = command;
    }

    public  RequestMessage(final String command) {
        _id = createId();
        _command = command;
    }

    public void setParameters(final Json parameters) {
        _parameters = parameters;
    }

    public Json getParameters() {
        return _parameters;
    }

    public Integer getId() {
        return _id;
    }

    public String getCommand() {
        return _command;
    }

    public Boolean isCommand(final ClientCommand clientCommand) {
        if (clientCommand == null) { return false; }
        return clientCommand.getValue().equals(_command);
    }

    public Boolean isCommand(final ServerCommand serverCommand) {
        if (serverCommand == null) { return false; }
        return serverCommand.getValue().equals(_command);
    }


    @Override
    public Json toJson() {
        final Json message = new Json();
        message.put("id", _id);
        message.put("method", _command);
        message.put("params", _parameters);
        return message;
    }

    @Override
    public String toString() {
        final Json json = this.toJson();
        return json.toString();
    }
}

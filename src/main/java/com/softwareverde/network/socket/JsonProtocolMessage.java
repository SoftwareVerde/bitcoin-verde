package com.softwareverde.network.socket;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.util.StringUtil;

public class JsonProtocolMessage implements ProtocolMessage {
    protected final Json _message;

    public JsonProtocolMessage(final Json json) {
        _message = json;
    }

    public Json getMessage() {
        return _message;
    }

    @Override
    public ByteArray getBytes() {

        final String messageWithNewline;
        {
            final StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(_message);
            stringBuilder.append("\n");
            messageWithNewline = stringBuilder.toString();
        }

        return MutableByteArray.wrap(StringUtil.stringToBytes(messageWithNewline));
    }
}

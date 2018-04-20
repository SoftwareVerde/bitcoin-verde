package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.reader.ScriptReader;
import com.softwareverde.constable.list.List;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;

public class ScriptDeflater {
    public Json toJson(final Script script) {
        final Json json = new Json();
        json.put("bytes", HexUtil.toHexString(script.getBytes()));

        final Json operationsJson;
        {
            final List<Operation> operations = ScriptReader.getOperationList(script);
            if (operations != null) {
                operationsJson = new Json();
                for (final Operation operation : operations) {
                    operationsJson.add(operation);
                }
            }
            else {
                operationsJson = null;
            }
        }
        json.put("operations", operationsJson);

        return json;
    }
}

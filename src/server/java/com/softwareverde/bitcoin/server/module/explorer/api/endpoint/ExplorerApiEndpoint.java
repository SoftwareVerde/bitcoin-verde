package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.http.server.servlet.routed.json.JsonApplicationServlet;

public abstract class ExplorerApiEndpoint extends JsonApplicationServlet<Environment> {
    public ExplorerApiEndpoint(final Environment environment) {
        super(environment);
    }
}

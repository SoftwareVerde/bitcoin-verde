package com.softwareverde.bitcoin.server.module.node;

public interface BanFilter {
    Boolean isBanned(String host);
}

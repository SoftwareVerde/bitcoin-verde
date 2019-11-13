package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemInflater;

public interface InventoryItemInflaters extends Inflater {
    InventoryItemInflater getInventoryItemInflater();
}

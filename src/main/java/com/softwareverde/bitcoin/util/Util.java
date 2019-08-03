package com.softwareverde.bitcoin.util;

import java.util.ArrayList;
import java.util.Map;

public class Util extends com.softwareverde.util.Util {
    protected Util() { }

    public static <Key, Item> void addToListMap(final Key key, final Item item, final Map<Key, java.util.List<Item>> map) {
        java.util.List<Item> items = map.get(key);
        if (items == null) {
            items = new ArrayList<Item>();
            map.put(key, items);
        }

        items.add(item);
    }
}

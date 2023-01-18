package com.softwareverde.bitcoin.constable.util;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;

public class ConstUtil extends com.softwareverde.constable.util.ConstUtil {
    protected ConstUtil() { }

    public static <T extends Const> T asConstOrNull(final Constable<T> constable) {
        if (constable == null) { return null; }
        return constable.asConst();
    }

    public static <Key, Item> void addToListMap(final Key key, final Item item, final MutableHashMap<Key, MutableList<Item>> map) {
        MutableList<Item> items = map.get(key);
        if (items == null) {
            items = new MutableArrayList<>();
            map.put(key, items);
        }

        items.add(item);
    }
}

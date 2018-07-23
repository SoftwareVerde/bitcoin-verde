package com.softwareverde.bitcoin.constable.util;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.Constable;

public class ConstUtil {
    public static <T extends Const> T asConstOrNull(final Constable<T> constable) {
        if (constable == null) { return null; }
        return constable.asConst();
    }
}

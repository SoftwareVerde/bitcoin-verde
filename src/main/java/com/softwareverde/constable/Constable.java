package com.softwareverde.constable;

public interface Constable<T extends Const> {
    T asConst();
}

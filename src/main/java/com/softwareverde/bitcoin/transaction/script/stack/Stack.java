package com.softwareverde.bitcoin.transaction.script.stack;

import java.util.LinkedList;
import java.util.List;

public class Stack {
    public static final Value OVERFLOW_VALUE = Value.fromInteger(0);

    protected final List<Value> _values = new LinkedList<Value>();
    protected Boolean _didOverflow = false;

    protected Value _peak(final Integer index) {
        if ( (index < 0) || (index >= _values.size()) ) {
            _didOverflow = true;
            return OVERFLOW_VALUE;
        }

        return _values.get(_values.size() - index - 1);
    }

    public void push(final Value value) {
        _values.add(value);
    }

    public Value peak() {
        return _peak(0);
    }

    public Value peak(final Integer index) {
        return _peak(index);
    }

    public Value pop() {
        if (_values.isEmpty()) {
            _didOverflow = true;
            return OVERFLOW_VALUE;
        }

        return _values.remove(_values.size() - 1);
    }

    public Boolean isEmpty() {
        return _values.isEmpty();
    }

    public Integer getSize() {
        return _values.size();
    }

    public Boolean didOverflow() {
        return _didOverflow;
    }
}

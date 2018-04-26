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

    public Stack() { }

    public Stack(final Stack stack) {
        _values.addAll(stack._values);
        _didOverflow = stack._didOverflow;
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

    public Value pop(final Integer index) {
        if (index >= _values.size()) {
            _didOverflow = true;
            return OVERFLOW_VALUE;
        }

        return _values.remove(_values.size() - index - 1);
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

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < _values.size(); ++i) {
            final Value value = _peak(i);
            stringBuilder.append(value.toString());
            stringBuilder.append("\n");
        }

        return stringBuilder.toString();
    }
}

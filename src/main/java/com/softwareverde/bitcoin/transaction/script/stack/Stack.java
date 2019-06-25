package com.softwareverde.bitcoin.transaction.script.stack;

import java.util.LinkedList;
import java.util.List;

public class Stack {
    public static final Value OVERFLOW_VALUE = Value.fromInteger(0L);

    protected final List<Value> _values = new LinkedList<Value>();
    protected Boolean _didOverflow = false;

    protected Integer _maxItemCount = Integer.MAX_VALUE;
    protected Stack _altStack = null;

    protected Value _peak(final Integer index) {
        if ( (index < 0) || (index >= _values.size()) ) {
            _didOverflow = true;
            return OVERFLOW_VALUE;
        }

        return _values.get(_values.size() - index - 1);
    }

    protected void _initAltStack() {
        if (_altStack == null) {
            _altStack = new Stack();
        }
    }

    public Stack() { }

    public Stack(final Stack stack) {
        _values.addAll(stack._values);
        _didOverflow = stack._didOverflow;
        _maxItemCount = stack._maxItemCount;
        _altStack = ((stack._altStack != null) ? new Stack(stack._altStack) : null);
    }

    public void push(final Value value) {
        if (value == null) {
            _didOverflow = true;
            return;
        }

        final int totalItemCount = (_values.size() + (_altStack != null ? _altStack.getSize() : 0));
        if (totalItemCount >= _maxItemCount) {
            _didOverflow = true;
            return;
        }

        _values.add(value);
    }

    public void pushToAltStack(final Value value) {
        _initAltStack();
        _altStack.push(value);
    }

    public Value peak() {
        return _peak(0);
    }

    public Value peakFromAltStack() {
        _initAltStack();
        return _altStack.peak();
    }

    public Value peak(final Integer index) {
        return _peak(index);
    }

    public Value peakFromAltStack(final Integer index) {
        _initAltStack();
        return _altStack.peak(index);
    }

    public Value pop() {
        if (_values.isEmpty()) {
            _didOverflow = true;
            return OVERFLOW_VALUE;
        }

        return _values.remove(_values.size() - 1);
    }

    public Value popFromAltStack() {
        _initAltStack();
        return _altStack.pop();
    }

    public Value pop(final Integer index) {
        if ( (index < 0) || (index >= _values.size()) ) {
            _didOverflow = true;
            return OVERFLOW_VALUE;
        }

        return _values.remove(_values.size() - index - 1);
    }

    public Value popFromAltStack(final Integer index) {
        _initAltStack();
        return _altStack.pop(index);
    }

    /**
     * Removes all items from the primary stack.
     *  The altStack is not affected.
     */
    public void clearStack() {
        _values.clear();
    }

    /**
     * Removes all items from the alt stack.
     *  The primary stack is not affected.
     */
    public void clearAltStack() {
        if (_altStack != null) {
            _altStack.clearStack();
        }
    }

    public Boolean isEmpty() {
        return _values.isEmpty();
    }

    public Boolean altStackIsEmpty() {
        _initAltStack();
        return _altStack.isEmpty();
    }

    public Integer getSize() {
        return _values.size();
    }

    public Integer getAltStackSize() {
        _initAltStack();
        return _altStack.getSize();
    }

    public Boolean didOverflow() {
        if (_altStack != null) {
            if (_altStack.didOverflow()) { return true; }
        }

        return (_didOverflow);
    }

    /**
     * Sets the maximum number of items allowed within the stack.
     *  Exceeding this value results in push operations to be ignored, and overflows the stack.
     *  This value includes the size of the stack and its altStack.
     */
    public void setMaxItemCount(final Integer maxItemCount) {
        _maxItemCount = maxItemCount;
    }

    public Integer getMaxItemCount() {
        return _maxItemCount;
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < _values.size(); ++i) {
            final Value value = _peak(i);
            stringBuilder.append(value.toString());
            stringBuilder.append("\n");
        }

        if (_altStack != null) {
            stringBuilder.append("-----");
            stringBuilder.append(_altStack.toString());
        }

        return stringBuilder.toString();
    }
}

package com.softwareverde.util;

public class CircleBuffer<T> {
    protected final T[] _items;
    protected int _writeIndex;
    protected int _readIndex;

    //              [ ] [ ] [ ] // Empty.                           Before: _writeIndex=0, _readIndex=0
    // ::pushItem   [X] [ ] [ ] // First item.                      Before: _writeIndex=0, _readIndex=0; After: _writeIndex=1, _readIndex=0
    // ::popItem    [_] [ ] [ ] // Popped item.                     Before: _writeIndex=1, _readIndex=0; After: _writeIndex=1, _readIndex=1
    // ::pushItem   [_] [X] [ ] //                                  Before: _writeIndex=3; _readIndex=0; After: _writeIndex=4
    // ::pushItem   [_] [X] [X] // First filling.                   Before: _writeIndex=3; _readIndex=0; After: _writeIndex=4
    // ::pushItem   [Y] [X] [X] // First overwriting of index 0.    Before: _writeIndex=4; _readIndex=0; After: _writeIndex=5

    @SuppressWarnings("unchecked")
    public CircleBuffer(final int itemCount) {
        _items = (T[]) new Object[itemCount];
        _writeIndex = 0;
        _readIndex = 0;
    }

    public synchronized void pushItem(final T item) {
        final int index = (_writeIndex % _items.length);
        _items[index] = item;

        _writeIndex += 1;

        if (_writeIndex - _readIndex > _items.length) {
            _readIndex = (_writeIndex - _items.length);
        }
    }

    public synchronized T popItem() {
        if (_readIndex >= _writeIndex) { return null; }

        final int index = (_readIndex % _items.length);
        _readIndex += 1;
        return _items[index];
    }

    public synchronized int getItemCount() {
        return (_writeIndex - _readIndex);
    }

    public int getMaxItemCount() {
        return _items.length;
    }
}
package com.softwareverde.util.bytearray;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteBuffer;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

public class ByteArrayStream extends ByteArrayReader implements AutoCloseable {
    protected ByteArray _lastPage = null;
    protected Integer _indexOfLastPage = null;

    protected final LinkedList<InputStream> _inputStreams = new LinkedList<>();
    InputStream _inputStream;

    protected byte[] _readFromStream(final Integer byteCount) {
        if (_inputStream == null) { return new byte[0]; }

        final ByteBuffer byteBuffer = new ByteBuffer();
        byteBuffer.setPageByteCount(byteCount);

        int byteCountRead = 0;
        while (byteCountRead < byteCount) {
            final int readResult;
            final byte[] readBuffer;
            try {
                readBuffer = byteBuffer.getRecycledBuffer();
                readResult = _inputStream.read(readBuffer);
            }
            catch (final IOException exception) { break; }
            if (readResult < 0) { break; }
            else if (readResult > 0) {
                byteBuffer.appendBytes(readBuffer, readResult);
                byteCountRead += readResult;
            }
        }

        return byteBuffer.readBytes(byteCountRead);
    }

    @Override
    protected byte _getByte(final int index) {
        final byte[] bytes = _getBytes(index, 1, Endian.BIG);
        return bytes[0];
    }

    @Override
    protected byte[] _getBytes(final int index, final int byteCount, final Endian endian) {
        if (index < Math.min(_index, Util.coalesce(_indexOfLastPage, Integer.MAX_VALUE))) {
            throw new UnsupportedOperationException("Cannot rewind stream. streamIndex=min(" + _index + ", " + _indexOfLastPage + "), readIndex=" + index);
        }

        final ByteBuffer byteBuffer = new ByteBuffer();
        int remainingByteCount = byteCount;

        final int lastPageByteCount = (_indexOfLastPage != null ? _lastPage.getByteCount() : 0);
        final int lastPageEndIndex = (_indexOfLastPage != null ? (_indexOfLastPage + lastPageByteCount) : 0);
        if ( (lastPageByteCount > 0) && (index < lastPageEndIndex) ) {
            final int indexOfLastPage = _indexOfLastPage; // NPE not possible.

            final int pageReadStartIndex = (index - indexOfLastPage);
            final int pageReadByteCount = Math.min(lastPageByteCount - pageReadStartIndex, byteCount);

            final byte[] pageByteArray = ByteUtil.copyBytes(_lastPage, pageReadStartIndex, pageReadByteCount);
            byteBuffer.appendBytes(pageByteArray, pageByteArray.length);

            remainingByteCount -= pageReadByteCount;
        }
        else {
            // The read index is beyond the last read page; if the read index is past the current index then skip forward in the stream.
            int skipByteCountRemaining = (index - _index);
            try {
                byte[] skipBuffer = new byte[0];
                while (skipByteCountRemaining > 0L) {
                    if (_inputStream == null) { break; }

                    final int skipSize = Math.min(1024, skipByteCountRemaining);
                    if (skipBuffer.length != skipSize) {
                        skipBuffer = new byte[skipSize];
                    }

                    final int skipResult = _inputStream.read(skipBuffer);
                    if (skipResult < 0) {
                        _swapInNextStream();
                        continue;
                    }

                    skipByteCountRemaining -= skipResult;
                }
            }
            catch (final IOException exception) {
                Logger.debug(exception);
            }
            if (skipByteCountRemaining > 0) {
                _ranOutOfBytes = true;
            }
        }

        int bytesReadFromStream = 0;
        while ( (remainingByteCount > 0) && (! _ranOutOfBytes) ) {
            final byte[] bytes = _readFromStream(remainingByteCount);
            bytesReadFromStream += bytes.length;

            if (bytes.length > 0) {
                byteBuffer.appendBytes(bytes, bytes.length);
                remainingByteCount -= bytes.length;
            }

            if (remainingByteCount > 0) {
                final Boolean streamWasAvailable = _swapInNextStream();
                if (! streamWasAvailable) {
                    _ranOutOfBytes = true;
                    break;
                }
            }
        }

        final MutableByteArray byteArray = MutableByteArray.wrap(byteBuffer.readBytes(byteCount));

        if (bytesReadFromStream > 0) {
            _lastPage = byteArray.asConst(); // Particularly important as to not cache Little endian values in the next code block.
            _indexOfLastPage = index;
        }

        if (endian == Endian.LITTLE) {
            byteArray.reverseEndian();
        }

        return byteArray.unwrap();
    }

    @Override
    protected byte[] _consumeBytes(final int byteCount, final Endian endian) {
        final byte[] bytes = _getBytes(_index, byteCount, endian);
        _index += byteCount;

        if (_indexOfLastPage != null) {
            final int endIndexOfLastPage = (_indexOfLastPage + _lastPage.getByteCount());
            if (_index >= endIndexOfLastPage) {
                _lastPage = null;
                _indexOfLastPage = null;
            }
            else {
                final int pageOffset = (_index - _indexOfLastPage);
                final int keepByteCount = (_lastPage.getByteCount() - pageOffset);
                _lastPage = MutableByteArray.wrap(ByteUtil.copyBytes(_lastPage, pageOffset, keepByteCount));
                _indexOfLastPage = _index;
            }
        }

        return bytes;
    }

    @Override
    protected int _calculateRemainingByteCount() {
        throw new UnsupportedOperationException("Unable to determine remaining byte count of stream.");
    }

    @Override
    public void skipBytes(final Integer byteCount) {
        _consumeBytes(byteCount, Endian.BIG);
    }

    @Override
    public void setPosition(final Integer index) {
        if (index < _index) {
            throw new UnsupportedOperationException("Cannot rewind stream.");
        }

        final int offset = (index - _index);
        _consumeBytes(offset, Endian.BIG);
    }

    protected Boolean _swapInNextStream() {
        _inputStream = null;
        if (_inputStreams.isEmpty()) { return false; }

        _inputStream = _inputStreams.removeFirst();
        _ranOutOfBytes = false;
        return true;
    }

    public ByteArrayStream() {
        this(new InputStream[0]);
    }

    public ByteArrayStream(final InputStream... inputStreams) {
        super(new MutableByteArray(0));
        for (final InputStream inputStream : inputStreams) {
            _inputStreams.add(inputStream);
        }
        _swapInNextStream();
    }

    public ByteArrayStream(final List<InputStream> inputStreams) {
        super(new MutableByteArray(0));
        for (final InputStream inputStream : inputStreams) {
            _inputStreams.add(inputStream);
        }
        _swapInNextStream();
    }

    @Override
    public Boolean hasBytes() {
        if (_ranOutOfBytes) { return false; }

        if (_indexOfLastPage != null) {
            if ( (_index <= _indexOfLastPage) && (! _lastPage.isEmpty()) ) {
                return true;
            }
            else {
                final ByteArray lastPage = _lastPage;
                final Integer indexOfLastPage = _indexOfLastPage;
                final byte[] peakedBytes = _getBytes(_index, 1, Endian.BIG);

                final boolean didOverflow = _ranOutOfBytes;
                _ranOutOfBytes = false; // Unset the overflow flag for availability check...

                if (didOverflow) {
                    _lastPage = lastPage;
                    _indexOfLastPage = indexOfLastPage;
                    return false;
                }

                final int lastPageByteCount = lastPage.getByteCount();
                final MutableByteArray mutableByteArray = new MutableByteArray(lastPageByteCount + 1);
                ByteUtil.setBytes(mutableByteArray, lastPage, 0);
                mutableByteArray.setByte(lastPageByteCount, peakedBytes[0]);

                _lastPage = mutableByteArray;
                _indexOfLastPage = indexOfLastPage;

                return true;
            }
        }
        else {
            _getBytes(_index, 1, Endian.BIG);
            final boolean didOverflow = _ranOutOfBytes;
            _ranOutOfBytes = false; // Unset the overflow flag for availability check...
            return (! didOverflow);
        }
    }

    public void appendInputStream(final InputStream inputStream) {
        _inputStreams.addLast(inputStream);
        if ( _ranOutOfBytes || (_inputStream == null) ) {
            _swapInNextStream();
        }
    }

    @Override
    public void close() {
        Exception exception = null;
        for (final InputStream inputStream : _inputStreams) {
            try {
                inputStream.close();
            }
            catch (final Exception caughtException) {
                if (exception != null) {
                    exception.addSuppressed(caughtException);
                }
                else {
                    exception = caughtException;
                }
            }
        }

        if (exception != null) {
            Logger.debug(exception);
        }
    }
}

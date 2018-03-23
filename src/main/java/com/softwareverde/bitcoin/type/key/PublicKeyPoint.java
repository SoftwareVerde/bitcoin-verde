package com.softwareverde.bitcoin.type.key;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class PublicKeyPoint implements Constable<PublicKeyPoint>, Const {
    public enum Type {
        EXTENDED, COMPRESSED;
    }

    private static class Prefix {
        protected static final byte EXTENDED_PREFIX = 0x04;
        protected static final byte COMPRESSED_PREFIX_EVEN_Y = 0x02;
        protected static final byte COMPRESSED_PREFIX_ODD_Y = 0x03;

        private final Type _type;
        private final boolean _yCoordinateIsEven;

        protected Prefix(final Type type, final boolean yCoordinateIsEven) {
            _type = type;
            _yCoordinateIsEven = yCoordinateIsEven;
        }

        public Type getType() {
            return _type;
        }

        public byte getValue() {
            if (_type == Type.EXTENDED) { return EXTENDED_PREFIX; }
            return (_yCoordinateIsEven ? COMPRESSED_PREFIX_EVEN_Y : COMPRESSED_PREFIX_ODD_Y);
        }
    }

    private static boolean _coordinateIsEven(final ByteArray coordinate) {
        return ((coordinate.getByte(coordinate.getByteCount() - 1) & 0xFF) % 2 == 0);
    }

    /*
        final byte compressedPublicKeyPrefix = (yCoordinateIsEven ? (byte) 0x02 : (byte) 0x03);

        final byte[] compressedPublicKeyPoint = new byte[coordinateByteCount + prefixByteCount];
        {
            compressedPublicKeyPoint[0] = compressedPublicKeyPrefix;
            for (int i=0; i<publicKeyPointX.length; ++i) {
                compressedPublicKeyPoint[prefixByteCount + i] = publicKeyPointX[i];
            }
        }
     */

    private final Prefix _prefix;
    private final ByteArray _pointX;
    private final ByteArray _pointY;

    public PublicKeyPoint(final ByteArray bytes) {
        final boolean isCompressedFormat = (bytes.getByte(0) != Prefix.EXTENDED_PREFIX);

        if (isCompressedFormat) {
            throw new RuntimeException("PublicKeyPoint does not support compressed keys yet.");  // TODO: Derive pointY from pointX.
        }

        final ByteArray publicKeyPoint = Secp256k1.getPublicKeyPoint(bytes);
        final Integer coordinateByteCount = ((publicKeyPoint.getByteCount() - 1) / 2);

        final Integer prefixByteCount = 1;

        final byte[] publicKeyPointX = new byte[coordinateByteCount];
        final byte[] publicKeyPointY = new byte[coordinateByteCount];
        {
            for (int i=0; i<coordinateByteCount; ++i) {
                publicKeyPointX[i] = publicKeyPoint.getByte(prefixByteCount + i);
                publicKeyPointY[i] = publicKeyPoint.getByte(prefixByteCount + coordinateByteCount + i);
            }
        }

        _pointX = MutableByteArray.wrap(publicKeyPointX);
        _pointY = MutableByteArray.wrap(publicKeyPointY);

        final Boolean yCoordinateIsEven = _coordinateIsEven(_pointY);
        _prefix = new Prefix((isCompressedFormat ? Type.COMPRESSED : Type.EXTENDED), yCoordinateIsEven);
    }

    public byte getPrefix() {
        return _prefix.getValue();
    }

    public boolean yCoordinateIsEven() {
        return _coordinateIsEven(_pointY);
    }

    public ByteArray getXCoordinate() {
        return _pointX;
    }

    public ByteArray getYCoordinate() {
        return _pointY;
    }

    public boolean isCompressed() {
        return (_prefix.getType() == Type.COMPRESSED);
    }

    @Override
    public PublicKeyPoint asConst() {
        return this;
    }
}

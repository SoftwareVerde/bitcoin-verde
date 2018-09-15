/*
 * Copyright 2012 Matt Corallo
 * Copyright 2015 Andreas Schildbach
 * Copyright 2018 Josh Green <josh@softwareverde.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The original implementation of this code was derived from org.bitcoinj.core.BloomFilter ( https://github.com/bitcoinj/bitcoinj ).
 * Their work was derived from a public-domain implementation of MurmurHash3. ( https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp )
 */

package com.softwareverde.murmur;

import com.softwareverde.constable.bytearray.ByteArray;

public class MurmurHashUtil {
    private static int _rotateLeft32(final int x, final int r) {
        return ( (x << r) | (x >>> (32 - r)) );
    }

    public static long hashVersion3x86_32(final long nonce, final int functionIdentifier, final ByteArray object) {
        final int c1 = 0xCC9E2D51;
        final int c2 = 0x1B873593;

        int h1 = (int) (functionIdentifier * 0xFBA4C795L + nonce);

        final int objectByteCount = object.getByteCount();

        final int numBlocks = ( (objectByteCount / 4) * 4 );
        for (int i = 0; i < numBlocks; i += 4) {
            int k1 =    ( (object.getByte(i)        & 0xFF))
                     |  ( (object.getByte(i + 1) & 0xFF) << 8 )
                     |  ( (object.getByte(i + 2) & 0xFF) << 16 )
                     |  ( (object.getByte(i + 3) & 0xFF) << 24 );

            k1 *= c1;
            k1 = _rotateLeft32(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = _rotateLeft32(h1, 13);
            h1 = ( (h1 * 5) + 0xe6546b64 );
        }

        int k1 = 0;
        switch(objectByteCount & 0x03) {
            case 3: {
                k1 ^= ( (object.getByte(numBlocks + 2) & 0xFF) << 16 );
            } // Intentionally fall through...

            case 2: {
                k1 ^= ( (object.getByte(numBlocks + 1) & 0xFF) << 8 );
            } // Intentionally fall through...

            case 1: {
                k1 ^= ( object.getByte(numBlocks) & 0xFF );
                k1 *= c1;
                k1 = _rotateLeft32(k1, 15);
                k1 *= c2;
                h1 ^= k1;
            } // Intentionally fall through...

            default: {
                // Nothing.
            }
        }

        h1 ^= objectByteCount;
        h1 ^= h1 >>> 16;
        h1 *= 0x85EBCA6B;
        h1 ^= h1 >>> 13;
        h1 *= 0xC2B2AE35;
        h1 ^= h1 >>> 16;

        final long h1UnsignedInt = (h1 & 0xFFFFFFFFL);
        return h1UnsignedInt;
    }
}

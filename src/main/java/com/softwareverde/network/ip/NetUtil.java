//package com.softwareverde.network.ip;
//
//import com.softwareverde.bitcoin.type.bytearray.overflow.ImmutableOverflowingByteArray;
//import com.softwareverde.constable.bytearray.ByteArray;
//import com.softwareverde.util.Util;
//
//public class NetUtil {
//    static ByteArray pchIPv4;
//    static ByteArray pchOnionCat;
//
//    static {
//        {
//            final byte[] bytes = {(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0xff, (byte) 0xff};
//            pchIPv4 = new ImmutableOverflowingByteArray(bytes);
//        }
//        {
//            final byte[] bytes = { (byte) 0xFD, (byte) 0x87, (byte) 0xD8, (byte) 0x7E, (byte) 0xEB, (byte) 0x43 };
//            pchOnionCat = new ImmutableOverflowingByteArray(bytes);
//        }
//    }
//
//    public static Boolean isRFC3849(final Ip ip) {
//        final ByteArray bytes = new ImmutableOverflowingByteArray(ip.getBytes());
//        return (bytes.getByte(15) == (byte) 0x20 && bytes.getByte(14) == (byte) 0x01 && bytes.getByte(13) == (byte) 0x0D && bytes.getByte(12) == (byte) 0xB8);
//    }
//
//    public static Boolean isIPv4(final Ip ip) {
//        return (memcmp(ip, pchIPv4, sizeof(pchIPv4)) == 0);
//    }
//
//    public static Boolean isValid(final Ip ip) {
//        // Cleanup 3-byte shifted addresses caused by garbage in size field of addr
//        // messages from versions before 0.2.9 checksum.
//        // Two consecutive addr messages look like this:
//        // header20 vectorlen3 addr26 addr26 addr26 header20 vectorlen3 addr26
//        // addr26 addr26... so if the first length field is garbled, it reads the
//        // second batch of addr misaligned by 3 bytes.
//        // if (memcmp(ip, pchIPv4 + 3, sizeof(pchIPv4) - 3) == 0) return false;
//
//        // unspecified IPv6 address (::/128)
//        final byte[] ipNone6 = new byte[16];
//        if (Util.areEqual(ip.getBytes(), ipNone6)) { return false; }
//
//        // documentation IPv6 address
//        if (isRFC3849(ip)) { return false; }
//
//        if (IsIPv4()) {
//            // INADDR_NONE
//            uint32_t ipNone = INADDR_NONE;
//            if (memcmp(ip + 12, &ipNone, 4) == 0) return false;
//
//            // 0
//            ipNone = 0;
//            if (memcmp(ip + 12, &ipNone, 4) == 0) return false;
//        }
//
//        return true;
//    }
//
//    public Boolean isRoutable() {
//        return IsValid() &&
//            !(IsRFC1918() || IsRFC2544() || IsRFC3927() || IsRFC4862() ||
//            IsRFC6598() || IsRFC5737() || (IsRFC4193() && !IsTor()) ||
//            IsRFC4843() || IsLocal());
//    }
//}

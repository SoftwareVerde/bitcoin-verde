package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

import org.junit.Assert;
import org.junit.Test;

public class AddressTests {
    @Test
    public void should_calculate_checksum_0() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final ByteArray expectedChecksum = MutableByteArray.wrap(BitcoinUtil.base32StringToBytes("x64nx6hz"));

        final String prefix = "prefix";
        final ByteArray payloadBytes = new MutableByteArray(0);

        // Action
        final ByteArray checksumPayload = AddressInflater.buildBase32ChecksumPreImage(prefix, null, payloadBytes);
        final ByteArray calculatedChecksum = AddressInflater.calculateBase32Checksum(checksumPayload);

        // Assert
        Assert.assertEquals(expectedChecksum, calculatedChecksum);
    }

    @Test
    public void should_calculate_checksum_1() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final ByteArray expectedChecksum = MutableByteArray.wrap(BitcoinUtil.base32StringToBytes("gpf8m4h7"));

        final String prefix = "p";
        final ByteArray payloadBytes = new MutableByteArray(0);

        // Action
        final ByteArray checksumPayload = AddressInflater.buildBase32ChecksumPreImage(prefix, null, payloadBytes);
        final ByteArray calculatedChecksum = AddressInflater.calculateBase32Checksum(checksumPayload);

        // Assert
        Assert.assertEquals(expectedChecksum, calculatedChecksum);
    }

    @Test
    public void should_inflate_base32_address() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qr95sy3j9xwd2ap32xkykttr4cvcu7as4y0qverfuy";
        final Address expectedAddress = addressInflater.fromBase58Check("1KXrWXciRDZUpQwQmuM1DbwsKDLYAYsVLR");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_0() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qr6m7j9njldwwzlg9v7v53unlr4jkmx6eylep8ekg2";
        final Address expectedAddress = addressInflater.fromBase58Check("1PQPheJQSauxRPTxzNMUco1XmoCyPoEJCp");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_1() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bchtest:pr6m7j9njldwwzlg9v7v53unlr4jkmx6eyvwc0uz5t";
        final Address expectedAddress = addressInflater.fromBase58Check("1PQPheJQSauxRPTxzNMUco1XmoCyPoEJCp");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        // Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_2() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "pref:pr6m7j9njldwwzlg9v7v53unlr4jkmx6ey65nvtks5";
        final Address expectedAddress = addressInflater.fromBase58Check("1PQPheJQSauxRPTxzNMUco1XmoCyPoEJCp");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        // Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_3() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qzemu3vn2qlcfc47v84nxectx8x87n7q4cp4jn38c8";
        final Address expectedAddress = addressInflater.fromBase58Check("1HPPterRZy2Thr8kEtd4SAennyaFFEAngV");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_4() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qq3fwcmddtcpz6myvlw00s3dct90hsan7yvy7ekuq5";
        final Address expectedAddress = addressInflater.fromBase58Check("149uLAy8vkn1Gm68t5NoLQtUqBtngjySLF");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_5() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qp5vpegktrcup9zmtrtzy5r3kq79jqg8m5kddt39fu";
        final Address expectedAddress = addressInflater.fromBase58Check("1AYtMXDp7wmVyUzyZoras9V2gQFQRdCFEg");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_6() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qr7zk2cyfd2kqju84llaak6v03vpcanjscz8fawe0s";
        final Address expectedAddress = addressInflater.fromBase58Check("1PzLzi7dyY8kPQZdqekifFyqz2ab6SN7uh");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_7() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qzrswley4zhgdckhdpeysmgusg9g0we7cudk778l9s";
        final Address expectedAddress = addressInflater.fromBase58Check("1DJyCmsiabEimv45XXsvnoVQ39uw8jdskG");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_8() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qrl6rwwh0zz5ys2e3h7z98g9rzs82ckw3q9vrxpqgd";
        final Address expectedAddress = addressInflater.fromBase58Check("1QJf3qH213YvKtXixXVpZMxWsMTxcWCJAG");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_9() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qrwdvzrglm60j4q5d5kn3alznsfdfcpg2u37tje9ll";
        final Address expectedAddress = addressInflater.fromBase58Check("1M8g4U2WtvyT2DAgyBArYpu28UmJGBPCzu");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_10() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qrw4hpua2j264ewv76qkzmhgjtjyr5tk3c9txenyvk";
        final Address expectedAddress = addressInflater.fromBase58Check("1MBRyzaJB5j1ogKYcQgnJZwmRuogMhRUSJ");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_11() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qp2uucawq3epsjh98vntkytadppuvea6xvzh6xe37k";
        final Address expectedAddress = addressInflater.fromBase58Check("18phgVThQTVc6mSNoxF1UEZVaeYuDA3HAT");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }

    @Test
    public void should_inflate_base32_address_12() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32String = "bitcoincash:qpdrmmy8rqxv3x2fqlfmzmcjxt5tf6u7ry7yw6r27e";
        final Address expectedAddress = addressInflater.fromBase58Check("19E9zpHZ69Ru1D8R8pVEEXMRt8iqW3fcgG");

        // Action
        final Address address = addressInflater.fromBase32Check(base32String);

        // Assert
        Assert.assertEquals(expectedAddress, address);
        Assert.assertEquals(base32String, address.toBase32CheckEncoded());
    }
}

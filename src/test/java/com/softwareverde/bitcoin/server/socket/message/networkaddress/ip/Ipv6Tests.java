package com.softwareverde.bitcoin.server.socket.message.networkaddress.ip;

import org.junit.Assert;
import org.junit.Test;

public class Ipv6Tests {
    @Test
    public void should_parse_loopback_ip_string() {
        // Setup
        final String ipString = "0000:0000:0000:0000:0000:0000:0000:0001";
        final byte[] expectedBytes = new byte[] {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01
        };

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertArrayEquals(expectedBytes, ipv6.getBytes());
    }

    @Test
    public void should_parse_zeroed_ip_string() {
        // Setup
        final String ipString = "0000:0000:0000:0000:0000:0000:0000:0000";
        final byte[] expectedBytes = new byte[] {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
        };

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertArrayEquals(expectedBytes, ipv6.getBytes());
    }

    @Test
    public void should_parse_compact_ip_string() {
        // Setup
        final String ipString = ":FFFF:::0:A:1FF:1";
        final byte[] expectedBytes = new byte[] {
                (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0A,
                (byte) 0x01, (byte) 0xFF, (byte) 0x00, (byte) 0x01
        };

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertArrayEquals(expectedBytes, ipv6.getBytes());
    }

    @Test
    public void should_parse_max_ip_string() {
        // Setup
        final String ipString = "FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF";
        final byte[] expectedBytes = new byte[] {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertArrayEquals(expectedBytes, ipv6.getBytes());
    }

    @Test
    public void should_return_null_when_segment_is_not_hex() {
        // Setup
        final String ipString = "FFFF:FFFF:FFFF:ZZZZ:FFFF:FFFF:FFFF:FFFF";

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertNull(ipv6);
    }

    @Test
    public void should_return_null_when_segment_count_is_less_than_8() {
        // Setup
        final String ipString = "FFFF:FFFF:FFFF:FFFF";

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertNull(ipv6);
    }

    @Test
    public void should_return_null_when_segment_count_is_greater_than_8() {
        // Setup
        final String ipString = "FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF";

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertNull(ipv6);
    }

    @Test
    public void should_return_null_when_empty() {
        // Setup
        final String ipString = "";

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertNull(ipv6);
    }

    @Test
    public void should_return_null_when_whitespace() {
        // Setup
        final String ipString = "  \t   ";

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertNull(ipv6);
    }

    @Test
    public void should_parse_untrimmed_loopback_ip_string() {
        // Setup
        final String ipString = "  \t  0000:0000:0000:0000:0000:0000:0000:0001  \t  ";
        final byte[] expectedBytes = new byte[] {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01
        };

        // Action
        final Ipv6 ipv6 = Ipv6.parse(ipString);

        // Assert
        Assert.assertArrayEquals(expectedBytes, ipv6.getBytes());
    }
}

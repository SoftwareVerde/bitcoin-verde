package com.softwareverde.network.ip;

import org.junit.Assert;
import org.junit.Test;

public class Ipv4Tests {
    @Test
    public void should_parse_loopback_ip_string() {
        // Setup
        final String ipString = "127.0.0.1";
        final byte[] expectedBytes = new byte[] { (byte) 0x7F, (byte) 0x00, (byte) 0x00, (byte) 0x01 };

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertArrayEquals(expectedBytes, ipv4.getBytes());
    }

    @Test
    public void should_parse_zeroed_ip_string() {
        // Setup
        final String ipString = "0.0.0.0";
        final byte[] expectedBytes = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertArrayEquals(expectedBytes, ipv4.getBytes());
    }

    @Test
    public void should_parse_max_ip_string() {
        // Setup
        final String ipString = "255.255.255.255";
        final byte[] expectedBytes = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertArrayEquals(expectedBytes, ipv4.getBytes());
    }

    @Test
    public void should_return_null_when_segment_is_out_of_range() {
        // Setup
        final String ipString = "256.255.255.255";

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertNull(ipv4);
    }

    @Test
    public void should_return_null_when_segment_is_negative() {
        // Setup
        final String ipString = "0.-1.0.0";

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertNull(ipv4);
    }

    @Test
    public void should_return_null_when_segment_contains_alpha() {
        // Setup
        final String ipString = "0.a1.0.0";

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertNull(ipv4);
    }

    @Test
    public void should_return_null_when_segment_count_is_less_than_four() {
        // Setup
        final String ipString = "0.0.0";

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertNull(ipv4);
    }

    @Test
    public void should_return_null_when_segment_count_is_greater_than_four() {
        // Setup
        final String ipString = "0.0.0.0.0";

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertNull(ipv4);
    }

    @Test
    public void should_return_null_when_empty() {
        // Setup
        final String ipString = "";

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertNull(ipv4);
    }

    @Test
    public void should_return_null_when_whitespace() {
        // Setup
        final String ipString = " \t ";

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertNull(ipv4);
    }

    @Test
    public void should_parse_untrimmed_loopback_ip_string() {
        // Setup
        final String ipString = "  127.0.0.1\t \t  ";
        final byte[] expectedBytes = new byte[] { (byte) 0x7F, (byte) 0x00, (byte) 0x00, (byte) 0x01 };

        // Action
        final Ipv4 ipv4 = Ipv4.parse(ipString);

        // Assert
        Assert.assertArrayEquals(expectedBytes, ipv4.getBytes());
    }
}

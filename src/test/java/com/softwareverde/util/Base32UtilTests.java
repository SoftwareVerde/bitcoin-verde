package com.softwareverde.util;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import org.junit.Assert;
import org.junit.Test;

public class Base32UtilTests {
    @Test
    public void should_transform_to_and_from_base_32() {
        // Setup
        final ByteArray byteArray = ByteArray.fromHexString("E361CA9A7F99107C17A622E047E3745D3E19CF804ED63C5C40C6BA763696B98241223D8CE62AD48D863F4CB18C930E4C");

        // Action
        final String base32String = Base32Util.toBase32String(byteArray.getBytes());
        final byte[] bytes = Base32Util.base32StringToByteArray(base32String);

        // Assert
        Assert.assertEquals(byteArray, MutableByteArray.wrap(bytes));
        Assert.assertEquals(byteArray, MutableByteArray.wrap(bytes));
    }
}

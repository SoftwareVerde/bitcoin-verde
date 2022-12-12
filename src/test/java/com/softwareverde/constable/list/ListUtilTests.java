package com.softwareverde.constable.list;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.list.mutable.MutableList;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ListUtilTests extends UnitTest {
    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_reverse_list_empty() throws Exception {
        // Setup
        final MutableList<Integer> mutableList = new MutableList<>(0);

        // Action
        ListUtil.reverse(mutableList);

        // Assert
        Assert.assertEquals(0, mutableList.getCount());
    }

    @Test
    public void should_reverse_list_even() throws Exception {
        // Setup
        final MutableList<Integer> mutableList = new MutableList<>(8);
        for (int i = 0; i < 8; ++i) {
            mutableList.add(i);
        }

        // Action
        ListUtil.reverse(mutableList);

        // Assert
        Assert.assertEquals(Integer.valueOf(7), mutableList.get(0));
        Assert.assertEquals(Integer.valueOf(6), mutableList.get(1));
        Assert.assertEquals(Integer.valueOf(5), mutableList.get(2));
        Assert.assertEquals(Integer.valueOf(4), mutableList.get(3));
        Assert.assertEquals(Integer.valueOf(3), mutableList.get(4));
        Assert.assertEquals(Integer.valueOf(2), mutableList.get(5));
        Assert.assertEquals(Integer.valueOf(1), mutableList.get(6));
        Assert.assertEquals(Integer.valueOf(0), mutableList.get(7));
    }

    @Test
    public void should_reverse_list_odd() throws Exception {
        // Setup
        final MutableList<Integer> mutableList = new MutableList<>(7);
        for (int i = 0; i < 7; ++i) {
            mutableList.add(i);
        }

        // Action
        ListUtil.reverse(mutableList);

        // Assert
        Assert.assertEquals(Integer.valueOf(6), mutableList.get(0));
        Assert.assertEquals(Integer.valueOf(5), mutableList.get(1));
        Assert.assertEquals(Integer.valueOf(4), mutableList.get(2));
        Assert.assertEquals(Integer.valueOf(3), mutableList.get(3));
        Assert.assertEquals(Integer.valueOf(2), mutableList.get(4));
        Assert.assertEquals(Integer.valueOf(1), mutableList.get(5));
        Assert.assertEquals(Integer.valueOf(0), mutableList.get(6));
    }
}

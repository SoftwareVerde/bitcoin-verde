package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.map.Map;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.map.MutableVersionedHashMap;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class MutableVersionedHashMapTests extends UnitTest {
    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_visit_all_nodes() throws Exception {
        final int nodeCount = 128;
        final MutableVersionedHashMap<Integer, String> map = new MutableVersionedHashMap<>();

        for (int i = 0; i < nodeCount; ++i) {
            map.put(i, String.valueOf(i));
        }

        final AtomicInteger visitCount = new AtomicInteger(0);
        map.visit(new Map.Visitor<>() {
            @Override
            public boolean run(final Tuple<Integer, String> entry) {
                Assert.assertEquals(entry.first, Util.parseInt(entry.second));
                visitCount.incrementAndGet();
                return true;
            }
        });

        Assert.assertEquals(nodeCount, visitCount.get());
    }

    @Test
    public void should_visit_all_nodes_after_push_apply() throws Exception {
        final int nodeCount = 128;
        final MutableVersionedHashMap<Integer, String> map = new MutableVersionedHashMap<>();

        map.pushVersion();
        for (int i = 0; i < nodeCount; ++i) {
            map.put(i, String.valueOf(i));
        }
        map.applyVersion();

        final AtomicInteger visitCount = new AtomicInteger(0);
        map.visit(new Map.Visitor<>() {
            @Override
            public boolean run(final Tuple<Integer, String> entry) {
                Assert.assertEquals(entry.first, Util.parseInt(entry.second));
                visitCount.incrementAndGet();
                return true;
            }
        });

        Assert.assertEquals(nodeCount, visitCount.get());
    }

    @Test
    public void should_visit_all_nodes_after_push_pop_push_apply() throws Exception {
        final int nodeCount = 128;
        final MutableVersionedHashMap<Integer, String> map = new MutableVersionedHashMap<>();

        map.pushVersion();
        for (int i = nodeCount; i < (nodeCount * 2); ++i) {
            map.put(i, String.valueOf(i));
        }
        map.popVersion();

        map.pushVersion();
        for (int i = 0; i < nodeCount; ++i) {
            map.put(i, String.valueOf(i));
        }
        map.applyVersion();

        final AtomicInteger visitCount = new AtomicInteger(0);
        map.visit(new Map.Visitor<>() {
            @Override
            public boolean run(final Tuple<Integer, String> entry) {
                Assert.assertEquals(entry.first, Util.parseInt(entry.second));
                visitCount.incrementAndGet();
                return true;
            }
        });

        Assert.assertEquals(nodeCount, visitCount.get());
    }

    @Test
    public void should_get_versioned_values_after_push_pop_push_apply() throws Exception {
        final int nodeCount = 128;
        final MutableVersionedHashMap<Integer, String> map = new MutableVersionedHashMap<>();

        map.pushVersion();
        for (int i = nodeCount; i < (nodeCount * 2); ++i) {
            map.put(i, String.valueOf(i));
        }
        { // Test versioned get...
            Assert.assertEquals(null, map.get(nodeCount, 0));
            Assert.assertEquals(String.valueOf(nodeCount), map.get(nodeCount, 1));
        }
        map.popVersion();

        { // Test versioned get...
            Assert.assertEquals(null, map.get(nodeCount, 0));
            Assert.assertEquals(null, map.get(nodeCount, 1));
        }

        map.pushVersion();
        for (int i = 0; i < nodeCount; ++i) {
            map.put(i, String.valueOf(i));
        }
        { // Test versioned get...
            Assert.assertEquals(null, map.get(0, 0));
            Assert.assertEquals(String.valueOf(0), map.get(0, 1));
        }
        map.applyVersion();

        { // Test versioned get...
            Assert.assertEquals(String.valueOf(0), map.get(0, 0));
            Assert.assertEquals(String.valueOf(0), map.get(0, 1));
        }
    }
}

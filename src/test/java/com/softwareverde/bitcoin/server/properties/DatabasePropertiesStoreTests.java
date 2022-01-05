package com.softwareverde.bitcoin.server.properties;

import com.softwareverde.bitcoin.test.IntegrationTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DatabasePropertiesStoreTests extends IntegrationTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_store_and_get_updated_value() {
        // Setup
        final DatabasePropertiesStore databasePropertiesStore = new DatabasePropertiesStore(_databaseConnectionFactory);
        databasePropertiesStore.start();

        final String key = "KEY";

        // Action
        final Long value0 = databasePropertiesStore.getLong(key);
        databasePropertiesStore.set(key, 1L);
        final Long value1 = databasePropertiesStore.getLong(key);
        databasePropertiesStore.getAndSetLong(key, new PropertiesStore.GetAndSetter<Long>() {
            @Override
            public Long run(final Long value) {
                return (value + 1L);
            }
        });
        final Long value2 = databasePropertiesStore.getLong(key);

        // Assert
        Assert.assertNull(value0);
        Assert.assertEquals(1L, value1.longValue());
        Assert.assertEquals(2L, value2.longValue());

        databasePropertiesStore.stop();
    }
}

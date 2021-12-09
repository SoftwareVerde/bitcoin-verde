package com.softwareverde.bitcoin.server.properties;

import com.softwareverde.bitcoin.test.IntegrationTest;
import org.junit.Assert;
import org.junit.Test;

public class DatabasePropertiesStoreTests extends IntegrationTest {
    @Override
    public void before() throws Exception {
        super.before();
    }

    @Override
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
        final Long value0 = databasePropertiesStore.get(key);
        databasePropertiesStore.set(key, 1L);
        final Long value1 = databasePropertiesStore.get(key);
        databasePropertiesStore.getAndSet(key, new PropertiesStore.GetAndSetter() {
            @Override
            public Long run(final Long value) {
                return (value + 1L);
            }
        });
        final Long value2 = databasePropertiesStore.get(key);

        // Assert
        Assert.assertNull(value0);
        Assert.assertEquals(1L, value1.longValue());
        Assert.assertEquals(2L, value2.longValue());

        databasePropertiesStore.stop();
    }
}

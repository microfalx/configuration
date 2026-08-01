package net.microfalx.configuration;

import org.joor.Reflect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigurationServiceTest extends AbstractConfigurationTestCase {

    @Test
    void getMetadata() {
        assertEquals(2, configurationService.getRootMetadata().getChildren().size());
    }

    @Test
    void getRegistry() {
        assertNotNull(configurationService.getRegistry());
    }

    @Test
    void getProperty() {
        assertEquals("", configurationService.getProperty("a"));
        configurationSource.setProperty("a", 1);
        assertEquals("1", configurationService.getProperty("a"));
    }

    @Test
    void getPropertyNormalized() {
        configurationSource.setProperty("camelCase", 1);
        assertEquals("1", configurationService.getProperty("camelCase"));
        assertEquals("1", configurationService.getProperty("camel-case"));
        assertEquals("1", configurationService.getProperty("camel_case"));
    }

    @Test
    void getPropertyAsEnvironment() {
        configurationSource.setProperty("A", 1);
        assertEquals("1", configurationService.getProperty("a"));
    }

    @Test
    void getConfiguration() {
        Reflect.on(configurationService).call("registerMetadata");
        assertEquals("20", configurationService.getConfiguration().get("group1.group12.item1"));
        assertEquals("20", configurationService.getConfiguration().get("group1.group12.item1", "10"));
        assertEquals(20, configurationService.getConfiguration().get("group1.group12.item1", 10));
        assertEquals(false, configurationService.getConfiguration().get("group1.group12.item5", boolean.class, null));
    }

    @Test
    void convert() {
        assertEquals(1, configurationService.convert("a", "1", Integer.class));
    }


}
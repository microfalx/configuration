package net.microfalx.configuration;

import net.microfalx.registry.Registry;
import net.microfalx.threadpool.ThreadPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractConfigurationTestCase {

    @Mock private ThreadPool threadPool;

    @Spy protected Registry registry = Registry.get();
    @Spy protected InMemoryConfigurationSource configurationSource = new InMemoryConfigurationSource();

    @InjectMocks
    protected ConfigurationService configurationService;

    @BeforeEach
    void setup() throws Exception {
        configurationService.initialize();
        postSetup();
    }

    protected void postSetup() {

    }

    protected static class InMemoryConfigurationSource extends AbstractConfigurationSource {

        private final Map<String, Object> values = new HashMap<>();

        @Override
        public String getProperty(String key) {
            Object value = values.get(normalize(key));
            return value != null ? value.toString() : null;
        }

        public void setProperty(String key, Object value) {
            values.put(normalize(key), value);
        }
    }

}

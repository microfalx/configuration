package net.microfalx.configuration;

import java.util.HashMap;
import java.util.Map;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A configuration source which maps to System properties and environment variables.
 */
public class JvmConfigurationSource extends AbstractConfigurationSource {

    private final Map<String, String> environment = new HashMap<>();

    public JvmConfigurationSource() {
        normalizeAndCopy(System.getProperties(), environment);
        normalizeAndCopy(System.getenv(), environment);
    }

    @Override
    public String getProperty(String key) {
        requireNonNull(key);
        return environment.get(normalize(key));
    }


}

package net.microfalx.configuration;

import net.microfalx.lang.ObjectUtils;
import net.microfalx.lang.StringUtils;

import java.util.Map;

import static net.microfalx.lang.StringUtils.*;

/**
 * Base class for all configuration sources.
 * <p>
 * Key normalization is provided to allow any subclasses to have a consistent behaviour.
 */
public abstract class AbstractConfigurationSource implements ConfigurationSource {

    /**
     * Normalizes a key to provide consistent key lookups.
     *
     * @param key the key to normalize
     * @return the normalized key
     */
    protected final String normalize(String key) {
        String[] parts = StringUtils.split(key, ".");
        for (int i = 0; i < parts.length; i++) {
            String name = toIdentifier(parts[i]);
            parts[i] = StringUtils.replaceAll(name, "_", EMPTY_STRING);
        }
        return String.join(".", parts);
    }

    /**
     * Copies entries from one map to another, normalizing the keys.
     *
     * @param source      the source map
     * @param destination the destination map
     * @param <K>         the type of key
     * @param <V>         the type of value
     */
    protected <K, V> void normalizeAndCopy(Map<K, V> source, Map<String, String> destination) {
        for (Map.Entry<K, V> entry : source.entrySet()) {
            String key = ObjectUtils.toString(entry.getKey());
            String value = ObjectUtils.toString(entry.getValue());
            if (!isEmpty(key) && !isEmpty(value)) {
                destination.put(normalize(key), value);
            }
        }
    }
}

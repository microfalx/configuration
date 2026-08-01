package net.microfalx.configuration;

/**
 * An interface to an external source of values.
 * <p>
 * To provide a consistent behaviour, a null returned by the source will be converted
 * to an empty string.
 */
public interface ConfigurationSource {

    /**
     * Returns the value mapped to a given key.
     *
     * @param key the key
     * @return the value, empty if missing
     */
    String getProperty(String key);
}

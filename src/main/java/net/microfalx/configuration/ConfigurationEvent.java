package net.microfalx.configuration;

import lombok.Getter;
import lombok.ToString;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * An event triggered when configuration changed.
 */
@Getter
@ToString
public class ConfigurationEvent {

    private final Configuration configuration;
    private final Type type;
    private final String key;
    private final String previousValue;
    private final String currentValue;

    public ConfigurationEvent(Configuration configuration, Type type, String key) {
        requireNonNull(configuration);
        requireNonNull(type);
        requireNonNull(key);
        this.configuration = configuration;
        this.type = type;
        this.key = key;
        this.previousValue = null;
        this.currentValue = null;
    }

    public ConfigurationEvent(Configuration configuration, Type type, String key, String previousValue, String currentValue) {
        requireNonNull(configuration);
        requireNonNull(type);
        requireNonNull(key);
        this.configuration = configuration;
        this.type = type;
        this.key = key;
        this.previousValue = previousValue;
        this.currentValue = currentValue;
    }

    /**
     * Returns whether the event matches a given prefix for properties.
     *
     * @param prefix the prefix to check
     * @return {@code true} if a match, {@code false} otherwise
     */
    public boolean matchesProperties(String prefix) {
        return type == Type.PROPERTY && key.startsWith(prefix);
    }

    /**
     * Returns whether the event matches a group.
     *
     * @param prefix the prefix of the group
     * @return {@code true} if a match, {@code false} otherwise
     */
    public boolean matchesGroup(String prefix) {
        return type == Type.GROUP && key.startsWith(prefix);
    }

    /**
     * The event type
     */
    public enum Type {

        /**
         * A property changes
         */
        PROPERTY,

        /**
         * A group changed
         */
        GROUP
    }

}

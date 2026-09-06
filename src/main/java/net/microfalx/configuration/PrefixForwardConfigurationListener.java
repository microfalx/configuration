package net.microfalx.configuration;

/**
 * Forwards configuration events to another listener, filtering by a prefix.
 */
public class PrefixForwardConfigurationListener extends PrefixConfigurationListener {

    private final ConfigurationListener listener;

    public PrefixForwardConfigurationListener(String prefix, ConfigurationListener listener) {
        super(prefix);
        this.listener = listener;
    }

    @Override
    protected void onPrefixChange(ConfigurationEvent event) {
        listener.onEvent(event);
    }
}
